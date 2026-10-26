package com.bluup.hexwright.server.soulforge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class SoulBond {

    private static final String ROOT_TAG = "hexwright_soulbond";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_NAME = "Name";
    private static final String TAG_GENERATION = "Generation";
    private static final String TAG_INSTANCE = "Instance";
    private static final String TAG_FOR = "For";
    private static final String TAG_FOR_NAME = "ForName";
    private static final String TAG_DOOM = "DoomAt";
    private static final String TAG_SEVERED_BY = "SeveredBy";

    private SoulBond() {
    }

    public static boolean isBound(ItemStack stack) {
        return owner(stack) != null;
    }

    public static @Nullable UUID owner(ItemStack stack) {
        return uuid(stack, TAG_OWNER);
    }

    public static boolean isOwnedBy(ItemStack stack, Player player) {
        return player.getUUID().equals(owner(stack));
    }

    public static String ownerName(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null ? "" : tag.getString(TAG_NAME);
    }

    public static int generation(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag == null ? 0 : tag.getInt(TAG_GENERATION);
    }

    public static @Nullable UUID instance(ItemStack stack) {
        return uuid(stack, TAG_INSTANCE);
    }

    public static @Nullable UUID issuedTo(ItemStack stack) {
        return uuid(stack, TAG_FOR);
    }

    public static String issuedToName(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null ? "" : tag.getString(TAG_FOR_NAME);
    }

    public static long doomAt(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag == null ? 0L : tag.getLong(TAG_DOOM);
    }

    public static boolean isDoomed(ItemStack stack) {
        return doomAt(stack) != 0L;
    }

    public static @Nullable UUID severedBy(ItemStack stack) {
        return uuid(stack, TAG_SEVERED_BY);
    }

    static boolean isUnissued(ItemStack stack) {
        return owner(stack) == null && instance(stack) == null;
    }

    static void issue(ItemStack stack, UUID player, String name) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT_TAG);
        tag.putUUID(TAG_INSTANCE, UUID.randomUUID());
        tag.putUUID(TAG_FOR, player);
        tag.putString(TAG_FOR_NAME, name);
    }

    static void stamp(ItemStack stack, UUID owner, String name, int generation, UUID instance) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT_TAG);
        tag.remove(TAG_FOR);
        tag.remove(TAG_FOR_NAME);
        tag.putUUID(TAG_OWNER, owner);
        tag.putString(TAG_NAME, name);
        tag.putInt(TAG_GENERATION, generation);
        tag.putUUID(TAG_INSTANCE, instance);
    }

    static void doom(ItemStack stack, UUID severer, long doomAt) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT_TAG);
        long already = tag.getLong(TAG_DOOM);
        tag.remove(TAG_OWNER);
        tag.remove(TAG_NAME);
        tag.remove(TAG_GENERATION);
        tag.putLong(TAG_DOOM, already != 0L ? Math.min(already, doomAt) : doomAt);
        tag.putUUID(TAG_SEVERED_BY, severer);
    }

    static void lift(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        if (tag != null) {
            tag.remove(TAG_DOOM);
            tag.remove(TAG_SEVERED_BY);
        }
    }

    private static @Nullable CompoundTag tag(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getTagElement(ROOT_TAG);
    }

    private static @Nullable UUID uuid(ItemStack stack, String key) {
        CompoundTag tag = tag(stack);
        return tag != null && tag.hasUUID(key) ? tag.getUUID(key) : null;
    }
}
