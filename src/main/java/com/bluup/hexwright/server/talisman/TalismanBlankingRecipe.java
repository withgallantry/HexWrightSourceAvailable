package com.bluup.hexwright.server.talisman;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

public class TalismanBlankingRecipe extends ShapelessRecipe {

    public TalismanBlankingRecipe(ResourceLocation id, CraftingBookCategory category,
                                  ItemStack result, NonNullList<Ingredient> ingredients) {
        super(id, "", category, result, ingredients);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return super.matches(container, level) && findTalisman(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack talisman = findTalisman(container);
        if (talisman == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = talisman.copy();
        result.setCount(1);
        TalismanDesign.clear(result);
        return result;
    }

    private static ItemStack findTalisman(CraftingContainer container) {
        ItemStack found = null;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.getItem() instanceof TalismanItem) {
                if (found != null || TalismanDesign.isBlank(stack)) {
                    return null;
                }
                found = stack;
            }
        }
        return found;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HexwrightRecipes.TALISMAN_BLANKING;
    }

    public static final class Serializer implements RecipeSerializer<TalismanBlankingRecipe> {
        private static final ShapelessRecipe.Serializer DELEGATE = new ShapelessRecipe.Serializer();

        @Override
        public TalismanBlankingRecipe fromJson(ResourceLocation id, JsonObject json) {
            return upgrade(DELEGATE.fromJson(id, json));
        }

        @Override
        public TalismanBlankingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return upgrade(DELEGATE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, TalismanBlankingRecipe recipe) {
            DELEGATE.toNetwork(buffer, recipe);
        }

        private static TalismanBlankingRecipe upgrade(ShapelessRecipe parsed) {
            return new TalismanBlankingRecipe(
                parsed.getId(),
                parsed.category(),
                parsed.getResultItem(RegistryAccess.EMPTY),
                parsed.getIngredients()
            );
        }
    }
}
