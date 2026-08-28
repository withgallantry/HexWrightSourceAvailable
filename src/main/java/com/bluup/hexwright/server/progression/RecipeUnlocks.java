package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;

public final class RecipeUnlocks {

    private RecipeUnlocks() {
    }

    public static Set<String> all(ServerPlayer player) {
        return Collections.unmodifiableSet(
            RecipeUnlockState.get(player.server).unlocked(player.getUUID())
        );
    }

    public static boolean isUnlocked(ServerPlayer player, String recipeNameKey) {
        return RecipeUnlockState.get(player.server).unlocked(player.getUUID()).contains(recipeNameKey);
    }

    public static boolean canCraft(ServerPlayer player, String recipeNameKey) {
        return !RecipeTablets.requiresTablet(recipeNameKey) || isUnlocked(player, recipeNameKey);
    }

    public static boolean unlock(ServerPlayer player, String recipeNameKey) {
        if (!RecipeUnlockState.get(player.server).unlock(player.getUUID(), recipeNameKey)) {
            return false;
        }
        HexwrightNetworking.sendRecipeUnlockSync(player);
        return true;
    }
}
