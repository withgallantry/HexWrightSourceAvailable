package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.armour.GemArmourRecipe;
import com.bluup.hexwright.server.crucible.EssencePouchMergeRecipe;
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

    public static final RecipeSerializer<EssencePouchMergeRecipe> ESSENCE_POUCH_MERGE =
        new SimpleCraftingRecipeSerializer<>(EssencePouchMergeRecipe::new);

    private HexwrightRecipes() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Hexwright.id("talisman_blanking"), TALISMAN_BLANKING);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Hexwright.id("gem_armour"), GEM_ARMOUR);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Hexwright.id("vault_key_copy"), VAULT_KEY_COPY);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Hexwright.id("essence_pouch_merge"), ESSENCE_POUCH_MERGE);
    }
}
