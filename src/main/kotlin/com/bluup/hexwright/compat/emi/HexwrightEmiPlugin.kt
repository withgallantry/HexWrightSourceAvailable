package com.bluup.hexwright.compat.emi

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.compat.recipeviewer.ArmourCraftDisplay
import com.bluup.hexwright.compat.recipeviewer.EssenceForgeDisplay
import com.bluup.hexwright.server.block.HexwrightBlocks
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.recipe.EmiCraftingRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class HexwrightEmiPlugin : EmiPlugin {

    override fun register(registry: EmiRegistry) {
        registry.addCategory(ESSENCE_FORGE)
        registry.addWorkstation(ESSENCE_FORGE, EmiStack.of(ItemStack(HexwrightBlocks.WORKTABLE_ITEM)))
        for (display in EssenceForgeDisplay.all()) {
            registry.addRecipe(EssenceForgeEmiRecipe(ESSENCE_FORGE, display))
        }
        for (display in ArmourCraftDisplay.all()) {
            registry.addRecipe(
                EmiCraftingRecipe(
                    display.toNineCells().map { EmiStack.of(it) },
                    EmiStack.of(display.result),
                    display.id
                )
            )
        }
    }

    private companion object {
        val ESSENCE_FORGE = object : EmiRecipeCategory(
            Hexwright.id("essence_forge"),
            EmiStack.of(ItemStack(HexwrightBlocks.WORKTABLE_ITEM))
        ) {
            override fun getName(): Component = Component.translatable("block.hexwright.worktable")
        }
    }
}
