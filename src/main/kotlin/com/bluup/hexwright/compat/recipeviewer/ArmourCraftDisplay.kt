package com.bluup.hexwright.compat.recipeviewer

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.armour.ArmourCraftLayouts
import com.bluup.hexwright.server.armour.ArmourGemData
import com.bluup.hexwright.server.armour.ArmourSet
import com.bluup.hexwright.server.armour.ArmourTier
import com.bluup.hexwright.server.armour.HexwrightArmour
import com.bluup.hexwright.server.pocketcaster.PocketCasterData
import net.minecraft.core.NonNullList
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.ShapedRecipe

data class ArmourCraftDisplay(
    val id: ResourceLocation,
    val width: Int,
    val height: Int,
    val inputs: List<ItemStack>,
    val result: ItemStack
) {
    fun toShapedRecipe(): ShapedRecipe {
        val ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY)
        inputs.forEachIndexed { index, stack ->
            ingredients[index] = if (stack.isEmpty) Ingredient.EMPTY else Ingredient.of(stack)
        }
        return ShapedRecipe(id, GROUP, CraftingBookCategory.EQUIPMENT, width, height, ingredients, result)
    }

    fun toNineCells(): List<ItemStack> {
        val cells = MutableList(9) { ItemStack.EMPTY }
        for (row in 0 until height) {
            for (col in 0 until width) {
                cells[row * 3 + col] = inputs[row * width + col]
            }
        }
        return cells
    }

    companion object {
        private const val GROUP = "hexwright:armour"

        fun all(): List<ArmourCraftDisplay> = buildList {
            for (set in ArmourSet.values()) {
                for (grade in PocketCasterData.Quality.values()) {
                    val gem = ArmourGemData.create(HexwrightArmour.gem(set), grade)
                    val tier = ArmourTier.forGrade(grade)
                    for (layout in ArmourCraftLayouts.ALL) {
                        add(
                            ArmourCraftDisplay(
                                id = Hexwright.id(
                                    "/gem_armour/${set.id()}/${grade.name.lowercase()}/${layout.piece.id()}"
                                ),
                                width = layout.width(),
                                height = layout.height(),
                                inputs = buildList {
                                    for (row in 0 until layout.height()) {
                                        for (col in 0 until layout.width()) {
                                            add(ArmourCraftLayouts.displayStack(layout.at(row, col), gem))
                                        }
                                    }
                                },
                                result = ItemStack(HexwrightArmour.piece(set, tier, layout.piece))
                            )
                        )
                    }
                }
            }
        }
    }
}
