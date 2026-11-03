package com.bluup.hexwright.server.block

import com.bluup.hexwright.client.block.ResonanceTowerVisualClient
import com.bluup.hexwright.server.sound.HexwrightSoundEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class ResonanceTowerBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.RESONANCE_TOWER_BLOCK_ENTITY, pos, state), Container {

    companion object {
        const val POUCH_SLOT = 0
        const val CONTAINER_SIZE = 1

        const val BASE_RADIUS = 48.0
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    private var lastBroadcast = 0L


    fun dockedPouch(): ItemStack = items[POUCH_SLOT]

    fun dockPouch(pouch: ItemStack): ItemStack {
        val previous = items[POUCH_SLOT]
        items[POUCH_SLOT] = pouch
        syncActiveState()
        setChanged()
        return previous
    }

    fun radius(): Double = BASE_RADIUS

    fun markUsed() {
        val level = this.level ?: return
        super.setChanged()
        if (level.gameTime - lastBroadcast >= 20L) {
            lastBroadcast = level.gameTime
            level.sendBlockUpdated(worldPosition, blockState, blockState, 3)
        }
    }

    private fun syncActiveState() {
        val level = this.level ?: return
        val shouldBeActive = !items[POUCH_SLOT].isEmpty
        val state = blockState
        if (state.hasProperty(HexwrightBlockStates.ACTIVE) && state.getValue(HexwrightBlockStates.ACTIVE) != shouldBeActive) {
            level.setBlock(worldPosition, state.setValue(HexwrightBlockStates.ACTIVE, shouldBeActive), 3)
            if (shouldBeActive && !level.isClientSide) {
                level.playSound(
                    null, worldPosition,
                    HexwrightSoundEvents.resonanceTowerActivate(), SoundSource.BLOCKS, 1.0f, 1.0f
                )
            }
        }
    }

    fun clientTick() {
        val level = this.level ?: return
        val activeNow = blockState.hasProperty(HexwrightBlockStates.ACTIVE) && blockState.getValue(HexwrightBlockStates.ACTIVE)
        ResonanceTowerVisualClient.setActive(level, worldPosition, activeNow)
    }

    override fun setRemoved() {
        val lvl = level
        if (lvl != null && lvl.isClientSide) {
            ResonanceTowerVisualClient.setActive(lvl, worldPosition, false)
        }
        super.setRemoved()
    }


    override fun getContainerSize(): Int = CONTAINER_SIZE
    override fun isEmpty(): Boolean = items.all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = items[slot]

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val result = ContainerHelper.removeItem(items, slot, amount)
        if (!result.isEmpty) {
            syncActiveState()
            setChanged()
        }
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)

    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        syncActiveState()
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun clearContent() {
        for (i in items.indices) items[i] = ItemStack.EMPTY
    }


    override fun load(tag: CompoundTag) {
        super.load(tag)
        ContainerHelper.loadAllItems(tag, items)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }
}
