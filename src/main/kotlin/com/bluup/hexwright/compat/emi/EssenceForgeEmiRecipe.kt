package com.bluup.hexwright.compat.emi

import com.bluup.hexwright.compat.recipeviewer.EssenceForgeDisplay
import com.bluup.hexwright.compat.recipeviewer.EssenceForgePanel
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.render.EmiTexture
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EssenceForgeEmiRecipe(
    private val category: EmiRecipeCategory,
    private val display: EssenceForgeDisplay
) : EmiRecipe {

    private val input: EmiIngredient = EmiStack.of(display.pouch)
    private val results: List<EmiStack> = display.outputs.map { EmiStack.of(it) }
    private val cycledResults: EmiIngredient = EmiIngredient.of(results)

    override fun getCategory(): EmiRecipeCategory = category

    override fun getId(): ResourceLocation = display.id

    override fun getInputs(): List<EmiIngredient> = listOf(input)

    override fun getOutputs(): List<EmiStack> = results

    override fun getDisplayWidth(): Int = EssenceForgePanel.WIDTH

    override fun getDisplayHeight(): Int = EssenceForgePanel.height

    override fun supportsRecipeTree(): Boolean = false

    override fun addWidgets(widgets: WidgetHolder) {
        widgets.addTexture(EmiTexture.EMPTY_ARROW, EssenceForgePanel.ARROW_X, EssenceForgePanel.ARROW_Y)

        widgets.addSlot(
            input,
            EssenceForgePanel.POUCH_X - EssenceForgePanel.SLOT_BACKGROUND_INSET,
            EssenceForgePanel.SLOT_Y - EssenceForgePanel.SLOT_BACKGROUND_INSET
        )

        widgets.addSlot(
            cycledResults,
            EssenceForgePanel.OUTPUT_X - EssenceForgePanel.SLOT_BACKGROUND_INSET,
            EssenceForgePanel.SLOT_Y - EssenceForgePanel.SLOT_BACKGROUND_INSET
        )
            .recipeContext(this)

        widgets.addDrawable(0, 0, EssenceForgePanel.WIDTH, EssenceForgePanel.height) { graphics, _, _, _ ->
            EssenceForgePanel.draw(graphics, display)
        }
    }
}
