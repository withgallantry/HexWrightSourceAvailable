package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
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
import org.jetbrains.annotations.Nullable;

public class GemArmourRecipe extends CustomRecipe {

    public GemArmourRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return !resolve(container).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        return resolve(container);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HexwrightRecipes.GEM_ARMOUR;
    }

    private ItemStack resolve(CraftingContainer container) {
        int width = container.getWidth();
        int height = container.getHeight();

        int minCol = width, maxCol = -1, minRow = height, maxRow = -1;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (!container.getItem(row * width + col).isEmpty()) {
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                }
            }
        }
        if (maxCol < 0) {
            return ItemStack.EMPTY;
        }

        int usedWidth = maxCol - minCol + 1;
        int usedHeight = maxRow - minRow + 1;

        for (ArmourCraftLayouts.Layout layout : ArmourCraftLayouts.ALL) {
            if (layout.height() != usedHeight || layout.width() != usedWidth) {
                continue;
            }
            ItemStack result = tryLayout(container, layout, width, minCol, minRow);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack tryLayout(CraftingContainer container, ArmourCraftLayouts.Layout layout,
                                int width, int minCol, int minRow) {
        ArmourGemItem gem = null;
        PocketCasterData.Quality grade = null;

        for (int row = 0; row < layout.height(); row++) {
            for (int col = 0; col < layout.width(); col++) {
                ItemStack stack = container.getItem((minRow + row) * width + minCol + col);
                switch (layout.at(row, col)) {
                    case ArmourCraftLayouts.GEM -> {
                        if (!(stack.getItem() instanceof ArmourGemItem candidate)) {
                            return ItemStack.EMPTY;
                        }
                        PocketCasterData.Quality candidateGrade = ArmourGemData.getQuality(stack);
                        if (gem == null) {
                            gem = candidate;
                            grade = candidateGrade;
                        } else if (gem != candidate || grade != candidateGrade) {
                            return ItemStack.EMPTY;
                        }
                    }
                    case ArmourCraftLayouts.CRYSTALITE -> {
                        if (!stack.is(HexwrightItems.CRYSTALITE)) {
                            return ItemStack.EMPTY;
                        }
                    }
                    case ArmourCraftLayouts.LEATHER -> {
                        if (!stack.is(Items.LEATHER)) {
                            return ItemStack.EMPTY;
                        }
                    }
                    case ArmourCraftLayouts.EMPTY -> {
                        if (!stack.isEmpty()) {
                            return ItemStack.EMPTY;
                        }
                    }
                    default -> throw new IllegalStateException("bad armour layout char");
                }
            }
        }

        if (gem == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(HexwrightArmour.piece(gem.set(), ArmourTier.forGrade(grade), layout.piece()));
    }

    @Override
    public ItemStack getResultItem(@Nullable RegistryAccess registries) {
        return ItemStack.EMPTY;
    }
}
