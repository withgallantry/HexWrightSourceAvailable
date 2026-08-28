package com.bluup.hexwright.server.staff_assembly;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class StaffCoreData {
    private static final String ROOT_TAG = "hexwright_staff_core";
    private static final String TAG_QUALITY = "Quality";

    private StaffCoreData() {
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

    public static double gradeFraction(PocketCasterData.Quality quality) {
        return 0.2 + 0.8 * quality.ordinal() / (double) PocketCasterData.Quality.MASTERWORK.ordinal();
    }

    private static final int[] SCRIBE_CHAPTERS = {1, 2, 4, 6, 8};

    public static int scribeChapters(PocketCasterData.Quality quality) {
        return SCRIBE_CHAPTERS[Math.min(quality.ordinal(), SCRIBE_CHAPTERS.length - 1)];
    }

    private static final int[] AMETHYST_SLOTS = {2, 4, 6, 8, 10};

    public static int amethystSlots(PocketCasterData.Quality quality) {
        return AMETHYST_SLOTS[Math.min(quality.ordinal(), AMETHYST_SLOTS.length - 1)];
    }

    public static final double ECHO_MIN_IMPACT_AMBIT = 2.0;
    public static final double ECHO_MAX_IMPACT_AMBIT = 8.0;

    public static double echoImpactAmbit(PocketCasterData.Quality quality) {
        double fraction = quality.ordinal() / (double) PocketCasterData.Quality.MASTERWORK.ordinal();
        return ECHO_MIN_IMPACT_AMBIT + (ECHO_MAX_IMPACT_AMBIT - ECHO_MIN_IMPACT_AMBIT) * fraction;
    }
}
