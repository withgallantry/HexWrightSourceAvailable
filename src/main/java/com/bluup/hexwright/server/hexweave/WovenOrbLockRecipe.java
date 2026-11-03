package com.bluup.hexwright.server.hexweave;

import com.bluup.hexwright.server.talisman.HexwrightRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class WovenOrbLockRecipe extends CustomRecipe {

    public WovenOrbLockRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return !orb(container).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        ItemStack orb = orb(container);
        if (orb.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack locked = orb.copyWithCount(1);
        WovenOrbItem.lock(locked);
        return locked;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HexwrightRecipes.WOVEN_ORB_LOCK;
    }

    private static ItemStack orb(CraftingContainer container) {
        ItemStack orb = ItemStack.EMPTY;
        boolean obsidian = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(HexLoom.WOVEN_ORB) && orb.isEmpty()
                && WovenOrbItem.hasWeave(stack) && !WovenOrbItem.isLocked(stack)) {
                orb = stack;
            } else if (stack.is(Items.OBSIDIAN) && !obsidian) {
                obsidian = true;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return obsidian ? orb : ItemStack.EMPTY;
    }
}
