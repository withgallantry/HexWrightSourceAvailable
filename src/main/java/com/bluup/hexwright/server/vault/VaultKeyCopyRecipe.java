package com.bluup.hexwright.server.vault;

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

public class VaultKeyCopyRecipe extends CustomRecipe {

    public VaultKeyCopyRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return !cutKey(container).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        ItemStack source = cutKey(container);
        if (source.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = source.copy();
        copy.setCount(1);
        return copy;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < remaining.size(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(HexwrightItems.VAULT_KEY) && VaultKeyItem.boundVault(stack) != null) {
                ItemStack kept = stack.copy();
                kept.setCount(1);
                remaining.set(slot, kept);
                break;
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HexwrightRecipes.VAULT_KEY_COPY;
    }

    private static ItemStack cutKey(CraftingContainer container) {
        ItemStack cut = ItemStack.EMPTY;
        int blanks = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(HexwrightItems.VAULT_KEY)) {
                return ItemStack.EMPTY;
            }
            if (VaultKeyItem.boundVault(stack) == null) {
                if (++blanks > 1) {
                    return ItemStack.EMPTY;
                }
            } else if (cut.isEmpty()) {
                cut = stack;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return blanks == 1 ? cut : ItemStack.EMPTY;
    }
}
