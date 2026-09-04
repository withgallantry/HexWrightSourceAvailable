package com.bluup.hexwright.server.crucible;

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.talisman.HexwrightRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EssencePouchMergeRecipe extends CustomRecipe {

    public EssencePouchMergeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return chargedPouches(container).size() >= 2;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        List<ItemStack> charged = chargedPouches(container);
        if (charged.size() < 2) {
            return ItemStack.EMPTY;
        }

        ItemStack merged = charged.get(0).copy();
        merged.setCount(1);
        EssencePouchData.clear(merged);
        for (ItemStack pouch : charged) {
            for (Map.Entry<IngredientCategory, Double> entry : EssencePouchData.getAll(pouch).entrySet()) {
                EssencePouchData.add(merged, entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        boolean consumed = false;
        for (int slot = 0; slot < remaining.size(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(HexwrightItems.ENDLESS_POUCH)) {
                continue;
            }
            if (!consumed && !EssencePouchData.isEmpty(stack)) {
                consumed = true;
                continue;
            }
            ItemStack kept = stack.copy();
            kept.setCount(1);
            EssencePouchData.clear(kept);
            remaining.set(slot, kept);
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HexwrightRecipes.ESSENCE_POUCH_MERGE;
    }

    private static List<ItemStack> chargedPouches(CraftingContainer container) {
        List<ItemStack> charged = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(HexwrightItems.ENDLESS_POUCH)) {
                return List.of();
            }
            if (!EssencePouchData.isEmpty(stack)) {
                charged.add(stack);
            }
        }
        return charged;
    }
}
