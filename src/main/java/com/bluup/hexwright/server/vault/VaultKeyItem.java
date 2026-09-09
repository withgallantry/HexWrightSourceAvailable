package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.portal.PortalWindow;
import com.bluup.hexwright.server.progression.MakersMark;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VaultKeyItem extends Item implements ArtifactItem {

    public static final String TAG_VAULT_ID = "VaultId";
    public static final String TAG_GRADE = "Grade";
    public static final String TAG_ARTIFACT = "Artifact";

    private static final PocketCasterData.Quality LEGACY_GRADE = PocketCasterData.Quality.FINE;

    public VaultKeyItem(Properties properties) {
        super(properties);
    }

    public static ItemStack blank(PocketCasterData.Quality grade) {
        ItemStack stack = new ItemStack(HexwrightItems.VAULT_KEY);
        stack.getOrCreateTag().putString(TAG_GRADE, grade.name());
        return stack;
    }

    public static ItemStack artifact() {
        ItemStack stack = blank(PocketCasterData.Quality.MASTERWORK);
        stack.getOrCreateTag().putBoolean(TAG_ARTIFACT, true);
        return stack;
    }

    public static ItemStack forVault(int vaultId, PocketCasterData.Quality grade, boolean artifact) {
        ItemStack stack = artifact ? artifact() : blank(grade);
        stack.getOrCreateTag().putInt(TAG_VAULT_ID, vaultId);
        stack.getOrCreateTag().putBoolean(TAG_ARTIFACT, artifact);
        return stack;
    }

    public static @Nullable Integer boundVault(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_VAULT_ID)) {
            return null;
        }
        return tag.getInt(TAG_VAULT_ID);
    }

    public static PocketCasterData.Quality grade(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_GRADE)) {
            return LEGACY_GRADE;
        }
        return PocketCasterData.Quality.byName(tag.getString(TAG_GRADE));
    }

    @Override
    public boolean isArtifact(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        if (tag.contains(TAG_ARTIFACT)) {
            return tag.getBoolean(TAG_ARTIFACT);
        }
        return tag.contains(TAG_VAULT_ID)
            && tag.contains(TAG_GRADE)
            && PocketCasterData.Quality.byName(tag.getString(TAG_GRADE))
                == PocketCasterData.Quality.MASTERWORK;
    }

    public static Component tierLabel(ItemStack stack) {
        return ArtifactItem.is(stack) ? ArtifactItem.label()
            : Component.translatable(grade(stack).translationKey());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        Integer vaultId = boundVault(stack);

        if (vaultId != null) {
            VaultPortalSession session = VaultManager.sessionByOpener(serverPlayer.getUUID());
            if (session != null && session.vaultId() == vaultId) {
                VaultManager.requestCloseVault(serverPlayer);
                return InteractionResultHolder.consume(stack);
            }
        }

        if (VaultDimension.isVaultLevel(serverPlayer.serverLevel())) {
            serverPlayer.displayClientMessage(Component.translatable("hexwright.vault.inside_vault"), true);
            return InteractionResultHolder.fail(stack);
        }
        PortalWindow window = VaultPortalPlacement.windowInFrontOf(serverPlayer);
        if (window == null) {
            serverPlayer.displayClientMessage(Component.translatable("hexwright.vault.no_room"), true);
            return InteractionResultHolder.fail(stack);
        }

        if (vaultId == null) {
            if (VaultDimension.level(serverPlayer.server) == null) {
                serverPlayer.displayClientMessage(Component.translatable("hexwright.vault.no_dimension"), true);
                return InteractionResultHolder.fail(stack);
            }
            PocketCasterData.Quality grade = grade(stack);
            boolean artifact = ArtifactItem.is(stack);
            vaultId = VaultManager.createVault(serverPlayer, grade, artifact, null).id();
            stack.getOrCreateTag().putInt(TAG_VAULT_ID, vaultId);
            stack.getOrCreateTag().putString(TAG_GRADE, grade.name());
            stack.getOrCreateTag().putBoolean(TAG_ARTIFACT, artifact);
            serverPlayer.sendSystemMessage(Component.translatable("hexwright.vault.created", vaultId,
                tierLabel(stack)));
        }
        VaultManager.openVault(serverPlayer, vaultId, window);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level instanceof ServerLevel serverLevel) {
            VaultAccess.refreshMirror(serverLevel.getServer(), stack);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = Component.translatable("item.hexwright.vault_key.named", tierLabel(stack));
        return ArtifactItem.is(stack) ? name.copy().withStyle(ArtifactItem.COLOUR) : name;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MakersMark.appendTooltip(stack, tooltip);
        PocketCasterData.Quality grade = grade(stack);
        boolean artifact = ArtifactItem.is(stack);
        VaultRooms.Layout layout = VaultRooms.layoutFor(grade, artifact);
        Integer vaultId = boundVault(stack);
        if (vaultId == null) {
            tooltip.add(Component.translatable("hexwright.vault.key_tooltip_unbound")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("hexwright.vault.key_tooltip_blank_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("hexwright.vault.key_tooltip_bound", vaultId)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            appendAccess(stack, tooltip);
        }
        tooltip.add(sizeLine(layout)
            .withStyle(artifact ? ArtifactItem.COLOUR : grade.color()));
    }

    private static MutableComponent sizeLine(VaultRooms.Layout layout) {
        return switch (layout) {
            case ESTATE -> Component.translatable("hexwright.vault.key_tooltip_estate");
            case PLANE -> Component.translatable("hexwright.vault.key_tooltip_plane");
            default -> Component.translatable("hexwright.vault.key_tooltip_size",
                layout.interiorWidth(), layout.interiorDepth(), layout.interiorHeight());
        };
    }

    private static void appendAccess(ItemStack stack, List<Component> tooltip) {
        var tag = stack.getTag();
        if (tag == null) {
            return;
        }
        boolean allowedOnly = VaultRecord.Access.byName(tag.getString(VaultAccess.TAG_ACCESS))
            == VaultRecord.Access.ALLOWED_ONLY;
        tooltip.add(Component.translatable("hexwright.vault.access_tooltip",
                Component.translatable(allowedOnly
                    ? "hexwright.vault.access_allowed_only"
                    : "hexwright.vault.access_everyone"))
            .withStyle(allowedOnly ? ChatFormatting.GOLD : ChatFormatting.GRAY));

        var names = tag.getList(VaultAccess.TAG_ALLOWED, Tag.TAG_STRING);
        for (int i = 0; i < names.size(); i++) {
            tooltip.add(Component.translatable("hexwright.vault.access_guest", names.getString(i))
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
