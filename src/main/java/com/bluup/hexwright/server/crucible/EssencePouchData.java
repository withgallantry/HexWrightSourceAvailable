package com.bluup.hexwright.server.crucible;

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class EssencePouchData {
    private static final String TAG_ESSENCE = "Essence";

    private EssencePouchData() {
    }

    public static double get(ItemStack pouch, IngredientCategory aspect) {
        CompoundTag essence = essenceTag(pouch);
        return essence == null ? 0.0 : essence.getDouble(key(aspect));
    }

    public static void add(ItemStack pouch, IngredientCategory aspect, double amount) {
        if (amount <= 0) {
            return;
        }
        CompoundTag essence = pouch.getOrCreateTag().getCompound(TAG_ESSENCE);
        essence.putDouble(key(aspect), essence.getDouble(key(aspect)) + amount);
        pouch.getOrCreateTag().put(TAG_ESSENCE, essence);
    }

    public static double consume(ItemStack pouch, IngredientCategory aspect, double amount) {
        CompoundTag essence = essenceTag(pouch);
        if (essence == null || amount <= 0) {
            return 0.0;
        }
        double stored = essence.getDouble(key(aspect));
        double taken = Math.min(stored, amount);
        double remaining = stored - taken;
        if (remaining <= 0) {
            essence.remove(key(aspect));
        } else {
            essence.putDouble(key(aspect), remaining);
        }
        return taken;
    }

    public static Map<IngredientCategory, Double> getAll(ItemStack pouch) {
        EnumMap<IngredientCategory, Double> result = new EnumMap<>(IngredientCategory.class);
        CompoundTag essence = essenceTag(pouch);
        if (essence == null) {
            return result;
        }
        for (IngredientCategory aspect : IngredientCategory.values()) {
            double amount = essence.getDouble(key(aspect));
            if (amount > 0) {
                result.put(aspect, amount);
            }
        }
        return result;
    }

    public static double total(ItemStack pouch) {
        double total = 0;
        for (double amount : getAll(pouch).values()) {
            total += amount;
        }
        return total;
    }

    public static void clear(ItemStack pouch) {
        CompoundTag tag = pouch.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(TAG_ESSENCE);
        if (tag.isEmpty()) {
            pouch.setTag(null);
        }
    }

    public static boolean isEmpty(ItemStack pouch) {
        CompoundTag essence = essenceTag(pouch);
        return essence == null || essence.isEmpty();
    }

    private static CompoundTag essenceTag(ItemStack pouch) {
        CompoundTag tag = pouch.getTag();
        if (tag == null || !tag.contains(TAG_ESSENCE)) {
            return null;
        }
        return tag.getCompound(TAG_ESSENCE);
    }

    private static String key(IngredientCategory aspect) {
        return aspect.name().toLowerCase(Locale.ROOT);
    }
}
