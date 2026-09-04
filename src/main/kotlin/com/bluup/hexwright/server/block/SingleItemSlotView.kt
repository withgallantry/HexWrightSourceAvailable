package com.bluup.hexwright.server.block

import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class SingleItemSlotView(private val delegate: Container) : Container {

    override fun getMaxStackSize(): Int = 1

    override fun getContainerSize(): Int = delegate.containerSize

    override fun isEmpty(): Boolean = delegate.isEmpty

    override fun getItem(slot: Int): ItemStack = delegate.getItem(slot)

    override fun removeItem(slot: Int, amount: Int): ItemStack = delegate.removeItem(slot, amount)

    override fun removeItemNoUpdate(slot: Int): ItemStack = delegate.removeItemNoUpdate(slot)

    override fun setItem(slot: Int, stack: ItemStack) = delegate.setItem(slot, stack)

    override fun setChanged() = delegate.setChanged()

    override fun stillValid(player: Player): Boolean = delegate.stillValid(player)

    override fun clearContent() = delegate.clearContent()

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = delegate.canPlaceItem(slot, stack)

    override fun startOpen(player: Player) = delegate.startOpen(player)

    override fun stopOpen(player: Player) = delegate.stopOpen(player)

    override fun countItem(item: Item): Int = delegate.countItem(item)

    override fun hasAnyOf(items: Set<Item>): Boolean = delegate.hasAnyOf(items)
}
