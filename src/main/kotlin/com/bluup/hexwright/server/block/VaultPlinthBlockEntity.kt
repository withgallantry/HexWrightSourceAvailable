package com.bluup.hexwright.server.block

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class VaultPlinthBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.VAULT_PLINTH_BLOCK_ENTITY, pos, state) {

    companion object {
        private const val TAG_ITEM = "Item"
    }

    var displayed: ItemStack = ItemStack.EMPTY
        private set

    fun stock(stack: ItemStack) {
        displayed = stack
        setChanged()
    }

    fun claim(player: Player): Boolean {
        if (displayed.isEmpty) {
            return false
        }
        val given = displayed.copy()
        displayed = ItemStack.EMPTY
        if (!player.inventory.add(given)) {
            player.drop(given, false)
        }
        setChanged()
        return true
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        displayed = if (tag.contains(TAG_ITEM)) {
            ItemStack.of(tag.getCompound(TAG_ITEM))
        } else {
            ItemStack.EMPTY
        }
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        if (!displayed.isEmpty) {
            tag.put(TAG_ITEM, displayed.save(CompoundTag()))
        }
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)
}
