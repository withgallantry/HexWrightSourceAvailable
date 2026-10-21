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

    private SoulBond() {
    }

    public static boolean isBound(ItemStack stack) {
        return owner(stack) != null;
    }

    public static @Nullable UUID owner(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag != null && tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    public static boolean isOwnedBy(ItemStack stack, Player player) {
        return player.getUUID().equals(owner(stack));
    }

    public static String ownerName(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null ? "" : tag.getString(TAG_NAME);
    }

    public static int generation(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null ? 0 : tag.getInt(TAG_GENERATION);
    }

    static void stamp(ItemStack stack, @Nullable UUID owner, String name, int generation) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT_TAG);
        if (owner == null) {
            tag.remove(TAG_OWNER);
            tag.remove(TAG_NAME);
        } else {
            tag.putUUID(TAG_OWNER, owner);
            tag.putString(TAG_NAME, name);
        }
        tag.putInt(TAG_GENERATION, generation);
    }
}
