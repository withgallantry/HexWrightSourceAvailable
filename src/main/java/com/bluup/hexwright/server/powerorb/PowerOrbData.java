package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class PowerOrbData {
    private static final String TAG_ROOT = "hexwright_power_orb";
    private static final String TAG_PATTERN = "Pattern";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_CONSTRUCT_HEALTH = "GolemHealth";
    private static final String TAG_READY_AT = "ReadyAt";

    private PowerOrbData() {
    }


    @Nullable
    public static CompoundTag getPatternTag(ItemStack orb) {
        HexPattern pattern = getPattern(orb);
        return pattern == null ? null : IotaType.serialize(new PatternIota(pattern));
    }

    @Nullable
    public static HexPattern getPattern(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        if (root == null || !root.contains(TAG_PATTERN, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag stored = root.getCompound(TAG_PATTERN);
        CompoundTag patternTag = IotaType.getTypeFromTag(stored) == HexIotaTypes.PATTERN
            ? stored.getCompound(HexIotaTypes.KEY_DATA)
            : stored;
        return HexPattern.isPattern(patternTag) ? HexPattern.fromNBT(patternTag) : null;
    }

    public static void setPattern(ItemStack orb, HexPattern pattern) {
        orb.getOrCreateTagElement(TAG_ROOT).put(TAG_PATTERN, IotaType.serialize(new PatternIota(pattern)));
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


    public static boolean holdsConstruct(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        return root != null && root.contains(TAG_CONSTRUCT_HEALTH, Tag.TAG_ANY_NUMERIC);
    }

    public static float constructHealth(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        return root == null ? 0.0F : root.getFloat(TAG_CONSTRUCT_HEALTH);
    }

    public static void setConstructHealth(ItemStack orb, float health) {
        orb.getOrCreateTagElement(TAG_ROOT).putFloat(TAG_CONSTRUCT_HEALTH, health);
    }

    public static void clearConstruct(ItemStack orb) {
        CompoundTag root = orb.getTagElement(TAG_ROOT);
        if (root != null) {
            root.remove(TAG_CONSTRUCT_HEALTH);
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
