package com.bluup.hexwright.server.block

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory
import com.bluup.hexwright.server.crucible.EssencePouchData
import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.network.EssenceNetwork
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class EssenceGaugeBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.ESSENCE_GAUGE_BLOCK_ENTITY, pos, state), Container {

    companion object {
        const val SOURCE_SLOT = 0
        const val CONTAINER_SIZE = 1

        const val ESSENCE_PER_LEVEL = 20.0
        const val POLL_INTERVAL = 10L
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    private var aspectOrdinal = 0
    private var cachedSignal = 0

    fun dockedSource(): ItemStack = items[SOURCE_SLOT]

    fun dockSource(source: ItemStack): ItemStack {
        val previous = items[SOURCE_SLOT]
        items[SOURCE_SLOT] = source
        setChanged()
        return previous
    }

    fun aspect(): IngredientCategory = IngredientCategory.values()[aspectOrdinal]

    fun cycleAspect(): IngredientCategory {
        aspectOrdinal = (aspectOrdinal + 1) % IngredientCategory.values().size
        setChanged()
        return aspect()
    }

    fun signal(): Int = cachedSignal

    fun serverTick() {
        val level = this.level ?: return
        if (level.gameTime % POLL_INTERVAL != 0L) return
        val pouch = EssenceNetwork.resolve(items[SOURCE_SLOT], level, worldPosition)
        val amount = if (pouch.item is EndlessPouchItem) EssencePouchData.get(pouch, aspect()) else 0.0
        val signal = (amount / ESSENCE_PER_LEVEL).toInt().coerceIn(0, 15)
        if (signal != cachedSignal) {
            cachedSignal = signal
            level.updateNeighbourForOutputSignal(worldPosition, blockState.block)
            setChanged()
        }
    }


    override fun getContainerSize(): Int = CONTAINER_SIZE
    override fun isEmpty(): Boolean = items.all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = items[slot]

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val result = ContainerHelper.removeItem(items, slot, amount)
        if (!result.isEmpty) setChanged()
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)

    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        slot == SOURCE_SLOT && EssenceNetwork.isEssenceSource(stack)

    override fun getMaxStackSize(): Int = 1

    override fun clearContent() {
        for (i in items.indices) items[i] = ItemStack.EMPTY
    }


    override fun load(tag: CompoundTag) {
        super.load(tag)
        ContainerHelper.loadAllItems(tag, items)
        aspectOrdinal = tag.getInt("Aspect").coerceIn(0, IngredientCategory.values().size - 1)
        cachedSignal = tag.getInt("Signal").coerceIn(0, 15)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
        tag.putInt("Aspect", aspectOrdinal)
        tag.putInt("Signal", cachedSignal)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }
}
