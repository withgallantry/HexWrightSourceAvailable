package com.bluup.hexwright.server.wardingbox;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class WardingBoxData {

    public static final String ROOT_TAG = "hexwright_warding_box";
    private static final String TAG_QUALITY = "Quality";
    private static final String TAG_MEDIA = "Media";
    private static final String TAG_SPELL = "Spell";
    private static final String TAG_SPELL_SIZE = "SpellSize";

    private WardingBoxData() {
    }

    public static long capacityFor(PocketCasterData.Quality quality) {
        long dust = switch (quality) {
            case CRUDE -> 100;
            case SOUND -> 300;
            case FINE -> 1000;
            case EXQUISITE -> 2500;
            case MASTERWORK -> 5000;
        };
        return dust * MediaConstants.DUST_UNIT;
    }

    public static ItemStack create(PocketCasterData.Quality quality) {
        ItemStack stack = new ItemStack(HexwrightBlocks.WARDING_BOX_ITEM);
        CompoundTag root = stack.getOrCreateTagElement(ROOT_TAG);
        root.putString(TAG_QUALITY, quality.name());
        return stack;
    }

    public static Optional<PocketCasterData.Quality> getQuality(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_QUALITY)) {
            return Optional.empty();
        }
        try {
            return Optional.of(PocketCasterData.Quality.valueOf(root.getString(TAG_QUALITY)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static void saveToItem(
        ItemStack stack,
        PocketCasterData.Quality quality,
        long media,
        CompoundTag spellTag,
        int spellSize
    ) {
        CompoundTag root = stack.getOrCreateTagElement(ROOT_TAG);
        root.putString(TAG_QUALITY, quality.name());
        if (media > 0) {
            root.putLong(TAG_MEDIA, media);
        }
        if (spellTag != null) {
            root.put(TAG_SPELL, spellTag);
            root.putInt(TAG_SPELL_SIZE, spellSize);
        }
    }

    public static long getMedia(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        return root == null ? 0L : root.getLong(TAG_MEDIA);
    }

    public static CompoundTag getSpellTag(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_SPELL)) {
            return null;
        }
        return root.getCompound(TAG_SPELL);
    }

    public static int getSpellSize(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        return root == null ? 0 : root.getInt(TAG_SPELL_SIZE);
    }
}
