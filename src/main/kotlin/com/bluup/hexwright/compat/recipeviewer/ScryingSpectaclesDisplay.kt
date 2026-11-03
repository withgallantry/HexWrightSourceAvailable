package com.bluup.hexwright.compat.recipeviewer

import at.petrak.hexcasting.common.lib.HexItems
import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.item.HexwrightItems
import com.bluup.hexwright.server.wardingbox.WardersSpectaclesItem
import net.minecraft.core.NonNullList
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.ShapelessRecipe

object ScryingSpectaclesDisplay {

    @JvmField
    val ID: ResourceLocation = Hexwright.id("/scrying_spectacles")

    fun inputs(): List<ItemStack> =
        listOf(ItemStack(HexwrightItems.WARDERS_SPECTACLES), ItemStack(HexItems.SCRYING_LENS))

    fun result(): ItemStack =
        ItemStack(HexwrightItems.WARDERS_SPECTACLES).also { WardersSpectaclesItem.fitScryingLens(it) }

    fun toShapelessRecipe(): ShapelessRecipe {
        val ingredients = NonNullList.create<Ingredient>()
        inputs().forEach { ingredients.add(Ingredient.of(it)) }
        return ShapelessRecipe(ID, "", CraftingBookCategory.EQUIPMENT, result(), ingredients)
    }
}
