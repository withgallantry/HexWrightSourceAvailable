package com.bluup.hexwright.compat.jei

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.compat.recipeviewer.EssenceForgeDisplay
import com.bluup.hexwright.compat.recipeviewer.EssenceForgePanel
import com.bluup.hexwright.server.block.HexwrightBlocks
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class EssenceForgeCategory(guiHelper: IGuiHelper) : IRecipeCategory<EssenceForgeDisplay> {

    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(HexwrightBlocks.WORKTABLE_ITEM))
    private val arrow: IDrawable = guiHelper.recipeArrow
    private val title: Component = Component.translatable("block.hexwright.worktable")

    override fun getRecipeType(): RecipeType<EssenceForgeDisplay> = RECIPE_TYPE

    override fun getTitle(): Component = title

    override fun getIcon(): IDrawable = icon

    override fun getWidth(): Int = EssenceForgePanel.WIDTH

    override fun getHeight(): Int = EssenceForgePanel.height

    override fun getRegistryName(recipe: EssenceForgeDisplay): ResourceLocation = recipe.id

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: EssenceForgeDisplay, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, EssenceForgePanel.POUCH_X, EssenceForgePanel.SLOT_Y)
            .setStandardSlotBackground()
            .addItemStack(recipe.pouch)

        builder.addSlot(RecipeIngredientRole.OUTPUT, EssenceForgePanel.OUTPUT_X, EssenceForgePanel.SLOT_Y)
            .setOutputSlotBackground()
            .addItemStacks(recipe.outputs)
    }

    override fun draw(
        recipe: EssenceForgeDisplay,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        arrow.draw(graphics, EssenceForgePanel.ARROW_X, EssenceForgePanel.ARROW_Y)
        EssenceForgePanel.draw(graphics, recipe)
    }

    companion object {
        val RECIPE_TYPE: RecipeType<EssenceForgeDisplay> =
            RecipeType(Hexwright.id("essence_forge"), EssenceForgeDisplay::class.java)
    }
}
