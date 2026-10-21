package com.bluup.hexwright.compat.jei

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.compat.recipeviewer.ArmourCraftDisplay
import com.bluup.hexwright.compat.recipeviewer.EssenceForgeDisplay
import com.bluup.hexwright.compat.recipeviewer.ScryingSpectaclesDisplay
import com.bluup.hexwright.server.block.HexwrightBlocks
import com.bluup.hexwright.server.item.HexwrightItems
import mezz.jei.api.IModPlugin
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class HexwrightJeiPlugin : IModPlugin {

    override fun getPluginUid(): ResourceLocation = UID

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        registration.addRecipeCategories(EssenceForgeCategory(registration.jeiHelpers.guiHelper))
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        registration.addRecipes(EssenceForgeCategory.RECIPE_TYPE, EssenceForgeDisplay.all())
        registration.addRecipes(
            RecipeTypes.CRAFTING,
            ArmourCraftDisplay.all().map { it.toShapedRecipe() }
        )
        registration.addRecipes(RecipeTypes.CRAFTING, listOf(ScryingSpectaclesDisplay.toShapelessRecipe()))
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        registration.addRecipeCatalyst(
            ItemStack(HexwrightBlocks.WORKTABLE_ITEM),
            EssenceForgeCategory.RECIPE_TYPE
        )
    }

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        jeiRuntime.ingredientManager.removeIngredientsAtRuntime(
            VanillaTypes.ITEM_STACK,
            listOf(ItemStack(HexwrightItems.TEALS_STAFF))
        )
    }

    private companion object {
        val UID: ResourceLocation = Hexwright.id("jei_plugin")
    }
}
