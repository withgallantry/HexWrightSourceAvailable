package com.bluup.hexwright.server.armour;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ArmourPowerToggle {
    private static final String TAG_ROOT = "hexwright_armour_power";
    private static final String TAG_PATTERN = "Pattern";
    private static final String TAG_ENABLED = "Enabled";

    private ArmourPowerToggle() {
    }

    public static boolean hasPower(ArmourSet set) {
        return switch (set) {
            case VEILWALKER, CANTOR, HEXWARDEN, AUGUR, VENATOR, DOMITOR -> true;
        };
    }

    @Nullable
    public static CompoundTag getPatternTag(ItemStack chest) {
        CompoundTag root = chest.getTagElement(TAG_ROOT);
        if (root == null || !root.contains(TAG_PATTERN, Tag.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompound(TAG_PATTERN);
    }

    @Nullable
    public static HexPattern getPattern(ItemStack chest) {
        CompoundTag patternTag = getPatternTag(chest);
        return patternTag != null && HexPattern.isPattern(patternTag) ? HexPattern.fromNBT(patternTag) : null;
    }

    public static void setPattern(ItemStack chest, HexPattern pattern) {
        chest.getOrCreateTagElement(TAG_ROOT).put(TAG_PATTERN, pattern.serializeToNBT());
    }

    public static void clearPattern(ItemStack chest) {
        CompoundTag root = chest.getTagElement(TAG_ROOT);
        if (root != null) {
            root.remove(TAG_PATTERN);
        }
    }

    public static boolean matches(ItemStack chest, HexPattern pattern) {
        HexPattern bound = getPattern(chest);
        return bound != null && bound.getAngles().equals(pattern.getAngles());
    }

    public static boolean isEnabled(ItemStack chest) {
        CompoundTag root = chest.getTagElement(TAG_ROOT);
        return root == null || !root.contains(TAG_ENABLED) || root.getBoolean(TAG_ENABLED);
    }

    public static void setEnabled(ItemStack chest, boolean enabled) {
        chest.getOrCreateTagElement(TAG_ROOT).putBoolean(TAG_ENABLED, enabled);
    }


    public static final int REQUIRED_PIECES = 3;

    private static final EquipmentSlot[] ARMOUR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static final int SET_PIECES = ARMOUR_SLOTS.length;

    public static int wornPieces(@Nullable LivingEntity wearer, ArmourSet set) {
        if (wearer == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            if (wearer.getItemBySlot(slot).getItem() instanceof HexwrightArmourItem worn
                && worn.set() == set) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    public static ArmourTier weakestWornTier(@Nullable LivingEntity wearer, ArmourSet set) {
        if (wearer == null) {
            return null;
        }
        ArmourTier weakest = null;
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            if (wearer.getItemBySlot(slot).getItem() instanceof HexwrightArmourItem worn
                && worn.set() == set
                && (weakest == null || worn.tier().ordinal() < weakest.ordinal())) {
                weakest = worn.tier();
            }
        }
        return weakest;
    }

    @Nullable
    public static ArmourTier activeTier(@Nullable LivingEntity wearer, ArmourSet set) {
        if (wearer == null) {
            return null;
        }
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof HexwrightArmourItem piece)
            || piece.set() != set
            || piece.getType() != ArmorItem.Type.CHESTPLATE
            || !isEnabled(chest)) {
            return null;
        }
        return wornPieces(wearer, set) >= REQUIRED_PIECES ? weakestWornTier(wearer, set) : null;
    }
}
