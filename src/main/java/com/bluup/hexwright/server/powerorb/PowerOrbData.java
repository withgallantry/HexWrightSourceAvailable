package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class PowerOrbData {
    private static final String TAG_ROOT = "hexwright_power_orb";
    private static final String TAG_PATTERN = "Pattern";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_GOLEM_HEALTH = "GolemHealth";
    private static final String TAG_READY_AT = "ReadyAt";

    private PowerOrbData() {
    }


    @Nullable
    public static CompoundTag getPatternTag(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        if (root == null || !root.contains(TAG_PATTERN, Tag.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompound(TAG_PATTERN);
    }

    @Nullable
    public static HexPattern getPattern(ItemStack orb) {
        CompoundTag patternTag = getPatternTag(orb);
        return patternTag != null && HexPattern.isPattern(patternTag) ? HexPattern.fromNBT(patternTag) : null;
    }

    public static void setPattern(ItemStack orb, HexPattern pattern) {
        orb.getOrCreateTagElement(TAG_ROOT).put(TAG_PATTERN, pattern.serializeToNBT());
    }

    public static void clearPattern(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        if (root != null) {
            root.remove(TAG_PATTERN);
        }
    }

    public static boolean matches(ItemStack orb, HexPattern pattern) {
        HexPattern bound = getPattern(orb);
        return bound != null && bound.getAngles().equals(pattern.getAngles());
    }

    public static boolean isActive(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        return root != null && root.getBoolean(TAG_ACTIVE);
    }

    public static void setActive(ItemStack orb, boolean active) {
        orb.getOrCreateTagElement(TAG_ROOT).putBoolean(TAG_ACTIVE, active);
    }


    public static boolean holdsGolem(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        return root != null && root.contains(TAG_GOLEM_HEALTH, Tag.TAG_ANY_NUMERIC);
    }

    public static float golemHealth(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        return root == null ? 0.0F : root.getFloat(TAG_GOLEM_HEALTH);
    }

    public static void setGolemHealth(ItemStack orb, float health) {
        orb.getOrCreateTagElement(TAG_ROOT).putFloat(TAG_GOLEM_HEALTH, health);
    }

    public static void clearGolem(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        if (root != null) {
            root.remove(TAG_GOLEM_HEALTH);
        }
    }


    public static long readyAt(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        return root == null ? 0L : root.getLong(TAG_READY_AT);
    }

    public static void setReadyAt(ItemStack orb, long gameTime) {
        orb.getOrCreateTagElement(TAG_ROOT).putLong(TAG_READY_AT, gameTime);
    }

    public static long cooldownLeft(ItemStack orb, long gameTime) {
        return Math.max(0L, readyAt(orb) - gameTime);
    }
}
