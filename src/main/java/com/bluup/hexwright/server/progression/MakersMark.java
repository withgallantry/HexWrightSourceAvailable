package com.bluup.hexwright.server.progression;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class MakersMark {

    private static final String ROOT_TAG = "hexwright_makers_mark";
    private static final String TAG_NAME = "Name";
    private static final String TAG_ID = "Id";

    private MakersMark() {
    }

    public static void apply(ItemStack stack, ServerPlayer crafter) {
        CompoundTag root = stack.getOrCreateTagElement(ROOT_TAG);
        root.putString(TAG_NAME, crafter.getGameProfile().getName());
        root.putUUID(TAG_ID, crafter.getUUID());
    }

    public static Optional<String> crafterName(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_NAME)) {
            return Optional.empty();
        }
        String name = root.getString(TAG_NAME);
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        crafterName(stack).ifPresent(name ->
            tooltip.add(Component.translatable("tooltip.hexwright.makers_mark", name)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC))
        );
    }
}
