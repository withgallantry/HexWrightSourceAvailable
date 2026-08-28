package com.bluup.hexwright.server.talisman;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public final class TalismanDesign {

    public static final int WIDTH = 8;
    public static final int HEIGHT = 8;
    public static final int PIXELS = WIDTH * HEIGHT;
    public static final int PACKED_BYTES = PIXELS / 2;

    public static final int ORIGIN_X = 4;
    public static final int ORIGIN_Y = 5;

    public static final String ROOT_TAG = "hexwright_talisman_design";
    private static final String TAG_PIXELS = "Pixels";
    private static final String TAG_MASK = "Mask";

    private static final int NBT_TYPE_BYTE_ARRAY = 7;
    private static final int NBT_TYPE_LONG = 4;

    private static final int[] PALETTE = new int[16];

    static {
        for (DyeColor dye : DyeColor.values()) {
            PALETTE[dye.getId()] = 0xFF000000 | dye.getFireworkColor();
        }
    }

    private TalismanDesign() {
    }


    public static int argb(int paletteIndex) {
        return paletteIndex >= 0 && paletteIndex < PALETTE.length ? PALETTE[paletteIndex] : 0xFFFFFFFF;
    }


    public static boolean isBlank(ItemStack stack) {
        return getMask(stack) == 0L;
    }

    public static byte[] getPixels(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_PIXELS, NBT_TYPE_BYTE_ARRAY)) {
            return null;
        }
        byte[] pixels = root.getByteArray(TAG_PIXELS);
        return pixels.length == PACKED_BYTES ? pixels : null;
    }

    public static long getMask(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_MASK, NBT_TYPE_LONG) || getPixels(stack) == null) {
            return 0L;
        }
        return root.getLong(TAG_MASK);
    }

    public static void setDesign(ItemStack stack, byte[] packedPixels, long mask) {
        CompoundTag root = stack.getOrCreateTagElement(ROOT_TAG);
        root.putByteArray(TAG_PIXELS, packedPixels);
        root.putLong(TAG_MASK, mask);
    }

    public static void clear(ItemStack stack) {
        stack.removeTagKey(ROOT_TAG);
    }


    public static int index(int x, int y) {
        return y * WIDTH + x;
    }

    public static boolean isPainted(long mask, int index) {
        return (mask >>> index & 1L) != 0L;
    }

    public static int paletteIndexAt(byte[] packedPixels, int index) {
        int packed = packedPixels[index >> 1] & 0xFF;
        return (index & 1) == 0 ? packed & 0x0F : packed >> 4 & 0x0F;
    }

    public static byte[] pack(int[][] paletteIndices, boolean[][] painted) {
        byte[] packed = new byte[PACKED_BYTES];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (!painted[y][x]) {
                    continue;
                }
                int index = index(x, y);
                int value = paletteIndices[y][x] & 0x0F;
                packed[index >> 1] |= (byte) ((index & 1) == 0 ? value : value << 4);
            }
        }
        return packed;
    }

    public static long packMask(boolean[][] painted) {
        long mask = 0L;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (painted[y][x]) {
                    mask |= 1L << index(x, y);
                }
            }
        }
        return mask;
    }
}
