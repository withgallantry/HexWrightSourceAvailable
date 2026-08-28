package com.bluup.hexwright.server.block

import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import ram.talia.moreiotas.api.casting.iota.StringIota

class ResonantAnchorBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.RESONANT_ANCHOR_BLOCK_ENTITY, pos, state) {

    companion object {
        @JvmStatic
        fun keyOf(datum: Iota): String? = when (datum) {
            is DoubleIota -> "n:${datum.double}"
            is StringIota -> "s:${datum.string}"
            else -> null
        }
    }

    var attunementKey: String? = null
        private set

    fun attune(key: String) {
        val serverLevel = level as? ServerLevel ?: return
        val dimension: ResourceKey<Level> = serverLevel.dimension()
        attunementKey?.let {
            ResonantAnchorRegistry.get(serverLevel.server).unregister(it, dimension, worldPosition)
        }
        attunementKey = key
        ResonantAnchorRegistry.get(serverLevel.server).register(key, dimension, worldPosition)
        syncLit()
        setChanged()
    }

    fun installDungeonAnchor(key: String) {
        attunementKey = key
        setChanged()
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        val serverLevel = level as? ServerLevel ?: return
        val key = attunementKey ?: return
        serverLevel.server.execute {
            ResonantAnchorRegistry.get(serverLevel.server).register(key, serverLevel.dimension(), worldPosition)
        }
    }

    fun forget() {
        val serverLevel = level as? ServerLevel ?: return
        val key = attunementKey ?: return
        ResonantAnchorRegistry.get(serverLevel.server).unregister(key, serverLevel.dimension(), worldPosition)
    }

    private fun syncLit() {
        val state = blockState
        val tuned = attunementKey != null
        if (state.hasProperty(HexwrightBlockStates.ACTIVE) && state.getValue(HexwrightBlockStates.ACTIVE) != tuned) {
            level?.setBlock(worldPosition, state.setValue(HexwrightBlockStates.ACTIVE, tuned), 3)
        }
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        attunementKey = if (tag.contains("AttunementKey")) tag.getString("AttunementKey") else null
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        attunementKey?.let { tag.putString("AttunementKey", it) }
    }
}
