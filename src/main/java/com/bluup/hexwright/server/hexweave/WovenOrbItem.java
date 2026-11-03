package com.bluup.hexwright.server.hexweave;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WovenOrbItem extends Item implements DyeableLeatherItem {

    public static final String TAG_WEAVE = "Hexweave";
    public static final String TAG_LOCKED = "Locked";

    public WovenOrbItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getColor(ItemStack stack) {
        CompoundTag display = stack.getTagElement(TAG_DISPLAY);
        return display != null && display.contains(TAG_COLOR, Tag.TAG_ANY_NUMERIC)
            ? display.getInt(TAG_COLOR)
            : 0xFFFFFF;
    }

    public static boolean hasWeave(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_WEAVE, Tag.TAG_COMPOUND);
    }

    public static boolean isLocked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_LOCKED);
    }

    public static void lock(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(TAG_LOCKED, true);
    }

    public static void bind(ItemStack stack, Hexweave weave) {
        stack.getOrCreateTag().put(TAG_WEAVE, HexweaveNbt.write(weave));
    }

    public static void bind(ItemStack stack, Hexweave weave, WeaveLayout layout) {
        stack.getOrCreateTag().put(TAG_WEAVE, HexweaveNbt.write(weave, layout));
    }

    public static void clear(ItemStack stack) {
        stack.removeTagKey(TAG_WEAVE);
    }

    public static Hexweave weave(ItemStack stack) {
        if (!hasWeave(stack)) {
            throw new IllegalArgumentException("This Woven Orb carries no weave.");
        }
        return HexweaveNbt.read(stack.getTag().getCompound(TAG_WEAVE));
    }

    public static WeaveLayout layout(ItemStack stack) {
        return hasWeave(stack) ? HexweaveNbt.readLayout(stack.getTag().getCompound(TAG_WEAVE)) : WeaveLayout.NONE;
    }

    public static List<WeaveTrigger> listensFor(ItemStack stack) {
        return stack.getItem() instanceof WovenOrbItem && hasWeave(stack)
            ? HexweaveNbt.peekTriggers(stack.getTag().getCompound(TAG_WEAVE)) : List.of();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasWeave(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer holder) {
            WovenOrbTriggers.onUse(holder, hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (!hasWeave(stack)) {
            tooltip.add(Component.translatable("tooltip.hexwright.woven_orb.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        HexweaveNbt.Summary summary = HexweaveNbt.summarize(stack.getTag().getCompound(TAG_WEAVE));
        tooltip.add(summary.readable()
            ? Component.translatable("tooltip.hexwright.woven_orb.weave", summary.threads(), summary.links())
                .withStyle(ChatFormatting.LIGHT_PURPLE)
            : Component.translatable("tooltip.hexwright.woven_orb.unreadable").withStyle(ChatFormatting.DARK_GRAY));
        if (isLocked(stack)) {
            tooltip.add(Component.translatable("tooltip.hexwright.woven_orb.locked").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
