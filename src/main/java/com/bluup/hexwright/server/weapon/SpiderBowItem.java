package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpiderBowItem extends GreatBowItem implements ArtifactItem, FabricItem {

    private static final int SILK_COOLDOWN_TICKS = 100;

    private static final String ROOT_TAG = "hexwright_spider_bow";
    private static final String TAG_NEXT_SILK_TICK = "NextSilkTick";
    private static final String TAG_CAUGHT = "Caught";
    private static final String TAG_CAUGHT_NAME = "CaughtName";
    private static final String TAG_CAUGHT_BLOCK = "CaughtBlock";
    private static final String TAG_CAUGHT_DIMENSION = "CaughtDimension";

    public SpiderBowItem(Properties properties) {
        super(PocketCasterData.Quality.MASTERWORK, properties);
    }


    @Override
    protected boolean armsShots() {
        return false;
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return iota == null || iota instanceof PatternIota;
    }

    @Nullable
    public static HexPattern sigil(ItemStack bow) {
        if (!(bow.getItem() instanceof SpiderBowItem item)) {
            return null;
        }
        CompoundTag tag = item.readIotaTag(bow);
        if (tag == null || IotaType.getTypeFromTag(tag) != HexIotaTypes.PATTERN) {
            return null;
        }
        CompoundTag pattern = tag.getCompound(HexIotaTypes.KEY_DATA);
        return HexPattern.isPattern(pattern) ? HexPattern.fromNBT(pattern) : null;
    }

    public static ItemStack carriedWithSigil(Player archer, HexPattern pattern) {
        Inventory inventory = archer.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            HexPattern bound = sigil(stack);
            if (bound != null && bound.getAngles().equals(pattern.getAngles())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }


    public static void remember(Player archer, Entity caught) {
        CompoundTag root = catchTag(archer);
        if (root == null) {
            return;
        }
        root.remove(TAG_CAUGHT_BLOCK);
        root.remove(TAG_CAUGHT_DIMENSION);
        root.putUUID(TAG_CAUGHT, caught.getUUID());
        root.putString(TAG_CAUGHT_NAME, Component.Serializer.toJson(caught.getDisplayName()));
    }

    public static void remember(Player archer, BlockPos block) {
        CompoundTag root = catchTag(archer);
        if (root == null) {
            return;
        }
        root.remove(TAG_CAUGHT);
        root.putLong(TAG_CAUGHT_BLOCK, block.asLong());
        root.putString(TAG_CAUGHT_DIMENSION, archer.level().dimension().location().toString());
        root.putString(TAG_CAUGHT_NAME, Component.Serializer.toJson(Component.translatable(
            "tooltip.hexwright.spider_bow.caught_block", archer.level().getBlockState(block).getBlock().getName(),
            block.getX(), block.getY(), block.getZ())));
    }

    @Nullable
    private static CompoundTag catchTag(Player archer) {
        ItemStack bow = held(archer);
        return bow.isEmpty() ? null : bow.getOrCreateTagElement(ROOT_TAG);
    }

    @Nullable
    public static Iota caught(ItemStack bow, ServerLevel level) {
        CompoundTag root = bow.getTagElement(ROOT_TAG);
        if (root == null) {
            return null;
        }
        if (root.hasUUID(TAG_CAUGHT)) {
            Entity caught = level.getEntity(root.getUUID(TAG_CAUGHT));
            return caught != null && caught.isAlive() ? new EntityIota(caught) : null;
        }
        if (root.contains(TAG_CAUGHT_BLOCK)
            && level.dimension().location().toString().equals(root.getString(TAG_CAUGHT_DIMENSION))) {
            return new Vec3Iota(Vec3.atCenterOf(BlockPos.of(root.getLong(TAG_CAUGHT_BLOCK))));
        }
        return null;
    }

    private static ItemStack held(Player archer) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = archer.getItemInHand(hand);
            if (stack.getItem() instanceof SpiderBowItem) {
                return stack;
            }
        }
        Inventory inventory = archer.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof SpiderBowItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void prepareArrow(AbstractArrow arrow) {
        if (arrow instanceof HexArrowEntity hexArrow) {
            hexArrow.reportCatch();
        }
    }

    @Override
    public boolean allowNbtUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack,
                                           ItemStack newStack) {
        return false;
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (silkRemaining(stack, level) > 0L) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer archer) {
            startAbilityRecharge(stack, level, ROOT_TAG, TAG_NEXT_SILK_TICK, SILK_COOLDOWN_TICKS);
            SilkLineEntity.shoot(serverLevel, archer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static long silkRemaining(ItemStack stack, Level level) {
        return abilityRemaining(stack, level, ROOT_TAG, TAG_NEXT_SILK_TICK, SILK_COOLDOWN_TICKS);
    }

    @Override
    protected @Nullable Recharge powerRecharge(ItemStack stack, Level level, float partialTick) {
        return Recharge.of(silkRemaining(stack, level), SILK_COOLDOWN_TICKS, partialTick);
    }


    @Override
    public Component getName(ItemStack stack) {
        return WeaponTooltips.graded(stack, Component.translatable("item.hexwright.spider_bow"), null);
    }

    @Override
    protected void appendExtraTooltip(ItemStack stack, List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.spider_bow.silk",
            String.valueOf(Math.round(SilkLineEntity.REACH)), SilkLineEntity.WEB_TICKS / 20),
            ChatFormatting.DARK_PURPLE);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.spider_bow.recharge",
            String.format("%.1f", SILK_COOLDOWN_TICKS / 20.0F)), ChatFormatting.GRAY);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.spider_bow.spin",
            WebBallItem.AMBIT_TICKS / 1200), ChatFormatting.DARK_PURPLE);
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root != null && root.contains(TAG_CAUGHT_NAME)) {
            Component name = Component.Serializer.fromJson(root.getString(TAG_CAUGHT_NAME));
            if (name != null) {
                tooltip.add(Component.translatable("tooltip.hexwright.spider_bow.caught", name)
                    .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @Override
    protected Component uninscribedTooltip() {
        return Component.translatable("tooltip.hexwright.spider_bow.no_sigil");
    }
}
