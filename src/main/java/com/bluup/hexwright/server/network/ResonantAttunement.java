package com.bluup.hexwright.server.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ResonantAttunement {

    private static final String TAG_POS = "Tower";
    private static final String TAG_DIMENSION = "Dimension";

    private ResonantAttunement() {
    }

    public static void attune(ItemStack stack, String rootTag, Level level, BlockPos towerPos) {
        CompoundTag root = stack.getOrCreateTagElement(rootTag);
        root.put(TAG_POS, NbtUtils.writeBlockPos(towerPos));
        root.putString(TAG_DIMENSION, level.dimension().location().toString());
    }

    public static @Nullable BlockPos towerPos(ItemStack stack, String rootTag) {
        CompoundTag root = stack.getTagElement(rootTag);
        if (root == null || !root.contains(TAG_POS)) {
            return null;
        }
        return NbtUtils.readBlockPos(root.getCompound(TAG_POS));
    }

    public static String dimension(ItemStack stack, String rootTag) {
        CompoundTag root = stack.getTagElement(rootTag);
        return root == null ? "" : root.getString(TAG_DIMENSION);
    }

    public static boolean dimensionMatches(ItemStack stack, String rootTag, Level level) {
        return dimension(stack, rootTag).equals(level.dimension().location().toString());
    }

    public static @Nullable String networkKey(ItemStack stack, String rootTag) {
        BlockPos pos = towerPos(stack, rootTag);
        if (pos == null) {
            return null;
        }
        return dimension(stack, rootTag) + "@" + pos.asLong();
    }

    public static String networkKey(Level level, BlockPos towerPos) {
        return level.dimension().location().toString() + "@" + towerPos.asLong();
    }


    public static @Nullable BlockPos keyPos(String networkKey) {
        int split = networkKey.lastIndexOf('@');
        if (split < 0) {
            return null;
        }
        try {
            return BlockPos.of(Long.parseLong(networkKey.substring(split + 1)));
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    public static String keyDimension(String networkKey) {
        int split = networkKey.lastIndexOf('@');
        return split < 0 ? "" : networkKey.substring(0, split);
    }

    public static boolean withinTowerRange(String networkKey, String dimensionId, BlockPos pos) {
        BlockPos towerPos = keyPos(networkKey);
        if (towerPos == null || !keyDimension(networkKey).equals(dimensionId)) {
            return false;
        }
        double radius = EssenceNetwork.keyRange();
        return pos.distSqr(towerPos) <= radius * radius;
    }
}
