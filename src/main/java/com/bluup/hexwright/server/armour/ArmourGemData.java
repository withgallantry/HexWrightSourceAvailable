package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ArmourGemData {
    private static final String ROOT_TAG = "hexwright_armour_gem";
    private static final String TAG_QUALITY = "Quality";

    private ArmourGemData() {
    }

    public static ItemStack create(Item item, PocketCasterData.Quality quality) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTagElement(ROOT_TAG).putString(TAG_QUALITY, quality.name());
        return stack;
    }

    public static PocketCasterData.Quality getQuality(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null) {
            return PocketCasterData.Quality.CRUDE;
        }
        try {
            return PocketCasterData.Quality.valueOf(root.getString(TAG_QUALITY));
        } catch (IllegalArgumentException e) {
            return PocketCasterData.Quality.CRUDE;
        }
    }
}
