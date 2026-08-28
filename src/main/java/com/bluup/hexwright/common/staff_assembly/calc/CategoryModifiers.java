package com.bluup.hexwright.common.staff_assembly.calc;

import java.util.Map;
import java.util.Set;

public final class CategoryModifiers {

    public static final Map<IngredientCategory, Double> WRAP = Map.ofEntries(
        Map.entry(IngredientCategory.LIGHT, 1.25),
        Map.entry(IngredientCategory.FLEXIBLE, 1.25),
        Map.entry(IngredientCategory.ORGANIC, 1.10),
        Map.entry(IngredientCategory.THREAD, 1.30),
        Map.entry(IngredientCategory.LEATHER, 1.20),
        Map.entry(IngredientCategory.CRYSTAL, 0.75),
        Map.entry(IngredientCategory.METALLIC, 0.80),
        Map.entry(IngredientCategory.PRESTIGE, 0.70)
    );

    public static final Map<IngredientCategory, Double> FOCUS = Map.ofEntries(
        Map.entry(IngredientCategory.SPATIAL, 1.35),
        Map.entry(IngredientCategory.CRYSTAL, 1.25),
        Map.entry(IngredientCategory.ARCANE, 1.15),
        Map.entry(IngredientCategory.END, 1.20),
        Map.entry(IngredientCategory.ECHO, 1.20),
        Map.entry(IngredientCategory.LIGHT, 0.75),
        Map.entry(IngredientCategory.THREAD, 0.70)
    );

    public static final Map<IngredientCategory, Double> CATALYST = Map.ofEntries(
        Map.entry(IngredientCategory.RADIANT, 1.30),
        Map.entry(IngredientCategory.ENERGETIC, 1.30),
        Map.entry(IngredientCategory.FIRE, 1.25),
        Map.entry(IngredientCategory.NETHER, 1.20),
        Map.entry(IngredientCategory.ARCANE, 1.15),
        Map.entry(IngredientCategory.ORGANIC, 0.80),
        Map.entry(IngredientCategory.THREAD, 0.70)
    );

    private CategoryModifiers() {
    }

    public static double multiplierFor(Set<IngredientCategory> itemCategories, Map<IngredientCategory, Double> table) {
        double multiplier = 1.0;
        for (IngredientCategory category : itemCategories) {
            Double modifier = table.get(category);
            if (modifier != null) {
                multiplier *= modifier;
            }
        }
        return multiplier;
    }
}
