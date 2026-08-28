package com.bluup.hexwright.client.progression;

import com.bluup.hexwright.server.progression.RecipeTablets;

import java.util.Collection;
import java.util.Set;

public final class ClientRecipeUnlocks {

    private static volatile Set<String> unlocked = Set.of();

    private static volatile int version;

    private ClientRecipeUnlocks() {
    }

    public static void set(Collection<String> recipes) {
        unlocked = Set.copyOf(recipes);
        version++;
    }

    public static boolean has(String recipeNameKey) {
        return unlocked.contains(recipeNameKey);
    }

    public static boolean isDiscovered(String recipeNameKey) {
        return !RecipeTablets.requiresTablet(recipeNameKey) || has(recipeNameKey);
    }

    public static int version() {
        return version;
    }
}
