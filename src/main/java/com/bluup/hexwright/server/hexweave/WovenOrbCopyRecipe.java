package com.bluup.hexwright.server.hexweave;

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

public class WovenOrbCopyRecipe extends CustomRecipe {

    public WovenOrbCopyRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return find(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        Pair pair = find(container);
        if (pair == null) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = pair.blank().copyWithCount(1);
        copy.getOrCreateTag().put(WovenOrbItem.TAG_WEAVE,
            pair.woven().getTag().getCompound(WovenOrbItem.TAG_WEAVE).copy());
        return copy;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < remaining.size(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(HexLoom.WOVEN_ORB) && WovenOrbItem.hasWeave(stack)) {
                remaining.set(slot, stack.copyWithCount(1));
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
        return HexwrightRecipes.WOVEN_ORB_COPY;
    }

    private record Pair(ItemStack woven, ItemStack blank) {
    }

    private static Pair find(CraftingContainer container) {
        ItemStack woven = ItemStack.EMPTY;
        ItemStack blank = ItemStack.EMPTY;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(HexLoom.WOVEN_ORB) || WovenOrbItem.isLocked(stack)) {
                return null;
            }
            if (WovenOrbItem.hasWeave(stack)) {
                if (!woven.isEmpty()) {
                    return null;
                }
                woven = stack;
            } else {
                if (!blank.isEmpty()) {
                    return null;
                }
                blank = stack;
            }
        }
        return woven.isEmpty() || blank.isEmpty() ? null : new Pair(woven, blank);
    }
}
