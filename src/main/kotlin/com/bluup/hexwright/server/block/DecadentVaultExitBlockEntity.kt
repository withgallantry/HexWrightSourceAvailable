package com.bluup.hexwright.server.block

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import com.bluup.hexwright.server.worldgen.ruinedportal.RuinedPortalManager
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class DecadentVaultExitBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.DECADENT_VAULT_EXIT_BLOCK_ENTITY, pos, state) {

    companion object {
        private const val TAG_DIMENSION = "Dimension"
        private const val TAG_X = "X"
        private const val TAG_Y = "Y"
        private const val TAG_Z = "Z"
    }

    var portalDimension: ResourceKey<Level>? = null
        private set
    var portalPos: BlockPos? = null
        private set

    fun bindPortal(dimension: ResourceKey<Level>, pos: BlockPos) {
        portalDimension = dimension
        portalPos = pos
        setChanged()
    }

    fun sendHome(player: ServerPlayer): Boolean {
        val server = player.server
        val dimension = portalDimension ?: return false
        val pos = portalPos ?: return false
        val destination = server.getLevel(dimension) ?: return false
        destination.getChunk(pos.x shr 4, pos.z shr 4)
        val landing = RuinedPortalManager.landingBeside(destination, pos)
        RuinedPortalManager.markCooldown(player)
        player.teleportTo(destination, landing.x, landing.y, landing.z, player.yRot, player.xRot)
        return true
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        val dimensionId = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION))
        if (dimensionId != null) {
            portalDimension = ResourceKey.create(Registries.DIMENSION, dimensionId)
            portalPos = BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z))
        }
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        val dimension = portalDimension ?: return
        val pos = portalPos ?: return
        tag.putString(TAG_DIMENSION, dimension.location().toString())
        tag.putInt(TAG_X, pos.x)
        tag.putInt(TAG_Y, pos.y)
        tag.putInt(TAG_Z, pos.z)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)
}
