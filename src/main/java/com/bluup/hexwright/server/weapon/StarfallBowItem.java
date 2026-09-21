package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public class StarfallBowItem extends GreatBowItem implements ArtifactItem {

    private static final int HEX_COOLDOWN_TICKS = 30;

    private static final int SKYFALL_COOLDOWN_TICKS = 60;

    private static final String ROOT_TAG = "hexwright_starfall_bow";
    private static final String TAG_NEXT_SKYFALL_TICK = "NextSkyfallTick";

    public StarfallBowItem(Properties properties) {
        super(PocketCasterData.Quality.MASTERWORK, properties);
    }

    @Override
    protected int hexCooldownTicks() {
        return HEX_COOLDOWN_TICKS;
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
        if (skyfallRemaining(stack, level) > 0L) {
            return InteractionResultHolder.fail(stack);
        }

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer archer) {
            setNextSkyfallTick(stack, level.getGameTime() + SKYFALL_COOLDOWN_TICKS);
            CompoundTag hex = readIotaTag(stack);
            PiercingSkyfall.fire(serverLevel, archer, hex == null || hex.isEmpty() ? null : hex);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }


    private static long nextSkyfallTick(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        return root == null ? 0L : root.getLong(TAG_NEXT_SKYFALL_TICK);
    }

    private static void setNextSkyfallTick(ItemStack stack, long tick) {
        stack.getOrCreateTagElement(ROOT_TAG).putLong(TAG_NEXT_SKYFALL_TICK, tick);
    }

    private static long skyfallRemaining(ItemStack stack, Level level) {
        long remaining = nextSkyfallTick(stack) - level.getGameTime();
        return Math.max(0L, Math.min(remaining, SKYFALL_COOLDOWN_TICKS));
    }

    @Override
    protected float powerRechargeFraction(ItemStack stack, Level level, float partialTick) {
        return sweepOf(skyfallRemaining(stack, level), SKYFALL_COOLDOWN_TICKS, partialTick);
    }


    @Override
    public Component getName(ItemStack stack) {
        return WeaponTooltips.graded(stack, Component.translatable("item.hexwright.starfall_bow"), null);
    }

    @Override
    protected void appendExtraTooltip(ItemStack stack, List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.starfall_bow.skyfall",
            String.valueOf(Math.round(PiercingSkyfall.REACH))), ChatFormatting.DARK_PURPLE);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.starfall_bow.recharge",
            String.format("%.1f", SKYFALL_COOLDOWN_TICKS / 20.0F)), ChatFormatting.GRAY);
    }
}
