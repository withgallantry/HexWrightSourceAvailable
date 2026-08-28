package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.portal.PortalWindow;
import com.bluup.hexwright.server.progression.MakersMark;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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

public final class VaultKeyItem extends Item {

    public static final String TAG_VAULT_ID = "VaultId";
    public static final String TAG_GRADE = "Grade";

    private static final PocketCasterData.Quality LEGACY_GRADE = PocketCasterData.Quality.FINE;

    public VaultKeyItem(Properties properties) {
        super(properties);
    }

    public static ItemStack blank(PocketCasterData.Quality grade) {
        ItemStack stack = new ItemStack(HexwrightItems.VAULT_KEY);
        stack.getOrCreateTag().putString(TAG_GRADE, grade.name());
        return stack;
    }

    public static ItemStack forVault(int vaultId, PocketCasterData.Quality grade) {
        ItemStack stack = blank(grade);
        stack.getOrCreateTag().putInt(TAG_VAULT_ID, vaultId);
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
            vaultId = VaultManager.createVault(serverPlayer, grade, null).id();
            stack.getOrCreateTag().putInt(TAG_VAULT_ID, vaultId);
            stack.getOrCreateTag().putString(TAG_GRADE, grade.name());
            serverPlayer.sendSystemMessage(Component.translatable("hexwright.vault.created", vaultId,
                Component.translatable(grade.translationKey())));
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
        return Component.translatable("item.hexwright.vault_key.named",
            Component.translatable(grade(stack).translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MakersMark.appendTooltip(stack, tooltip);
        PocketCasterData.Quality grade = grade(stack);
        VaultRooms.Layout layout = VaultRooms.layoutFor(grade);
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
        tooltip.add((layout.isEstate()
            ? Component.translatable("hexwright.vault.key_tooltip_estate")
            : Component.translatable("hexwright.vault.key_tooltip_size",
                layout.interiorWidth(), layout.interiorDepth(), layout.interiorHeight()))
            .withStyle(grade.color()));
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
