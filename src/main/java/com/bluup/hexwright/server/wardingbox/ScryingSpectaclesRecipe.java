package com.bluup.hexwright.server.wardingbox;

import at.petrak.hexcasting.common.lib.HexItems;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.talisman.HexwrightRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class ScryingSpectaclesRecipe extends CustomRecipe {

    public ScryingSpectaclesRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return !spectacles(container).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        ItemStack spectacles = spectacles(container);
        if (spectacles.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = spectacles.copy();
        result.setCount(1);
        WardersSpectaclesItem.fitScryingLens(result);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HexwrightRecipes.SCRYING_SPECTACLES;
    }

    private static ItemStack spectacles(CraftingContainer container) {
        ItemStack spectacles = ItemStack.EMPTY;
        boolean lens = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(HexwrightItems.WARDERS_SPECTACLES) && spectacles.isEmpty()
                && !WardersSpectaclesItem.hasScryingLens(stack)) {
                spectacles = stack;
            } else if (stack.is(HexItems.SCRYING_LENS) && !lens) {
                lens = true;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return lens ? spectacles : ItemStack.EMPTY;
    }
}
