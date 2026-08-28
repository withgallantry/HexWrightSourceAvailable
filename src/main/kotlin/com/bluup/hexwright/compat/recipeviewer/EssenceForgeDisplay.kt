package com.bluup.hexwright.compat.recipeviewer

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.block.WorktableBlockEntity
import com.bluup.hexwright.server.block.WorktableRecipes
import com.bluup.hexwright.server.crucible.EssencePouchData
import com.bluup.hexwright.server.item.HexwrightItems
import com.bluup.hexwright.server.pocketcaster.PocketCasterData
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

data class EssenceForgeDisplay(
    val id: ResourceLocation,
    val name: Component,
    val steps: List<WorktableRecipes.InfusionStep>,
    val requiredMastery: PocketCasterData.Quality?,
    val graded: Boolean,
    val outputs: List<ItemStack>,
    val pouch: ItemStack
) {
    companion object {
        fun all(): List<EssenceForgeDisplay> = WorktableRecipes.RECIPES.map { recipe ->
            val pouch = ItemStack(HexwrightItems.ENDLESS_POUCH)
            for (step in recipe.steps) {
                EssencePouchData.add(pouch, step.aspect, step.amount)
            }
            EssenceForgeDisplay(
                id = Hexwright.id("/essence_forge/" + recipe.nameKey.replace('.', '/')),
                name = Component.translatable(recipe.nameKey),
                steps = recipe.steps,
                requiredMastery = recipe.requiredMastery,
                graded = recipe.graded,
                outputs = if (recipe.graded) {
                    PocketCasterData.Quality.values().map { grade -> recipe.assemble(grade) }
                } else {
                    listOf(recipe.assemble(PocketCasterData.Quality.CRUDE))
                },
                pouch = pouch
            )
        }
    }
}
