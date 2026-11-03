package com.bluup.hexwright.server.reliquary;

import at.petrak.hexcasting.api.item.VariantItem;
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
import java.util.Locale;

public class SatchelItem extends Item implements VariantItem, DyeableLeatherItem {

    private static final String ROOT_TAG = "hexwright_satchel";
    private static final String TAG_HELD = "Held";

    public static final String[] DESIGNS = {
        "camper", "frog", "goth", "school", "teddy_bear"
    };

    public enum Hook {
        OPEN,
        DEPOSIT,
        WITHDRAW;

        String tag() {
            return "Focus" + name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "hook.hexwright." + name().toLowerCase(Locale.ROOT);
        }
    }

    public SatchelItem(Properties properties) {
        super(properties);
    }

    @Override
    public int numVariants() {
        return DESIGNS.length + 1;
    }

    public static int designOf(ItemStack stack) {
        return stack.getItem() instanceof SatchelItem satchel ? satchel.getVariant(stack) : 0;
    }

    public static ItemStack getHeld(ItemStack satchel) {
        CompoundTag root = satchel.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_HELD)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(root.getCompound(TAG_HELD));
    }

    public static void setHeld(ItemStack satchel, ItemStack held) {
        CompoundTag root = satchel.getOrCreateTagElement(ROOT_TAG);
        if (held.isEmpty()) {
            root.remove(TAG_HELD);
        } else {
            root.put(TAG_HELD, held.copyWithCount(1).save(new CompoundTag()));
        }
    }

    public static ItemStack getFocus(ItemStack satchel, Hook hook) {
        CompoundTag root = satchel.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(hook.tag())) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(root.getCompound(hook.tag()));
    }

    public static void setFocus(ItemStack satchel, Hook hook, ItemStack focus) {
        CompoundTag root = satchel.getOrCreateTagElement(ROOT_TAG);
        if (focus.isEmpty()) {
            root.remove(hook.tag());
        } else {
            root.put(hook.tag(), focus.copyWithCount(1).save(new CompoundTag()));
        }
    }

    public static @Nullable String storeKey(ItemStack satchel) {
        ItemStack held = getHeld(satchel);
        if (!(held.getItem() instanceof ReliquarySealItem)) {
            return null;
        }
        return ReliquarySealItem.storeKey(held);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SatchelUIFactory.INSTANCE.openForHand(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ItemStack held = getHeld(stack);
        if (!held.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hexwright.satchel.holding", held.getHoverName())
                .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.satchel.unsealed")
                .withStyle(ChatFormatting.YELLOW));
        }
        int hooks = 0;
        for (Hook hook : Hook.values()) {
            if (!getFocus(stack, hook).isEmpty()) {
                hooks++;
            }
        }
        if (hooks > 0) {
            tooltip.add(Component.translatable("tooltip.hexwright.satchel.hooks", hooks)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

    }

    @Override
    public int getColor(ItemStack stack) {
        CompoundTag display = stack.getTagElement(TAG_DISPLAY);
        return display != null && display.contains(TAG_COLOR, Tag.TAG_ANY_NUMERIC)
            ? display.getInt(TAG_COLOR)
            : 0xFFFFFF;
    }
}
