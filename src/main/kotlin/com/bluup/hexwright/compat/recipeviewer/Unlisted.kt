package com.bluup.hexwright.compat.recipeviewer

import com.bluup.hexwright.server.dungeon.AncientPortals
import com.bluup.hexwright.server.item.HexwrightItems
import net.minecraft.world.item.Item

object Unlisted {

    fun items(): List<Item> =
        listOf<Item>(HexwrightItems.TEALS_STAFF) + AncientPortals.unlisted() + HexwrightItems.FLOURISHES.values
}
