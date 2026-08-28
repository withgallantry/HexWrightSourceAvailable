package com.bluup.hexwright.server.signet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class SignatureMark {
    public static final int WIDTH = 48;
    public static final int HEIGHT = 20;
    public static final int PACKED_BYTES = (WIDTH * HEIGHT) / 8;

    private static final String ROOT_TAG = "hexwright_signature";
    private static final String TAG_BITS = "Bits";
    private static final int NBT_TYPE_BYTE_ARRAY = 7;

    private SignatureMark() {
    }

    public static boolean hasMark(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        return root != null && root.contains(TAG_BITS, NBT_TYPE_BYTE_ARRAY);
    }

    public static byte[] getBits(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_BITS, NBT_TYPE_BYTE_ARRAY)) {
            return null;
        }
        return root.getByteArray(TAG_BITS);
    }

    public static void setMark(ItemStack stack, byte[] packedBits) {
        CompoundTag root = stack.getOrCreateTagElement(ROOT_TAG);
        root.putByteArray(TAG_BITS, packedBits);
    }

    public static void copyMark(ItemStack source, ItemStack target) {
        byte[] bits = getBits(source);
        if (bits != null) {
            setMark(target, bits);
        }
    }

    public static boolean isSet(byte[] packedBits, int x, int y) {
        int index = y * WIDTH + x;
        int byteIndex = index / 8;
        int bitIndex = index % 8;
        return byteIndex < packedBits.length && (packedBits[byteIndex] & (1 << bitIndex)) != 0;
    }

    public static byte[] pack(boolean[][] grid) {
        byte[] bits = new byte[PACKED_BYTES];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x]) {
                    int index = y * WIDTH + x;
                    bits[index / 8] |= (byte) (1 << (index % 8));
                }
            }
        }
        return bits;
    }
}
