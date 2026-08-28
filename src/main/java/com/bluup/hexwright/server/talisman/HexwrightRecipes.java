package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.armour.GemArmourRecipe;
import com.bluup.hexwright.server.vault.VaultKeyCopyRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

public final class HexwrightRecipes {

    public static final RecipeSerializer<TalismanBlankingRecipe> TALISMAN_BLANKING =
        new TalismanBlankingRecipe.Serializer();

    public static final RecipeSerializer<GemArmourRecipe> GEM_ARMOUR =
        new SimpleCraftingRecipeSerializer<>(GemArmourRecipe::new);

    public static final RecipeSerializer<VaultKeyCopyRecipe> VAULT_KEY_COPY =
        new SimpleCraftingRecipeSerializer<>(VaultKeyCopyRecipe::new);

    private HexwrightRecipes() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Hexwright.id("talisman_blanking"), TALISMAN_BLANKING);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Hexwright.id("gem_armour"), GEM_ARMOUR);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Hexwright.id("vault_key_copy"), VAULT_KEY_COPY);
    }
}
