package com.bluup.hexwright.server.block

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

class FieldMarkerBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.FIELD_MARKER_BLOCK_ENTITY, pos, state) {

    var fieldId: UUID? = null
        private set

    fun isTuned(): Boolean = fieldId != null

    fun commit(id: UUID) {
        fieldId = id
        syncLit()
        setChanged()
    }

    fun forget() {
        val serverLevel = level as? ServerLevel ?: return
        val id = fieldId ?: return
        val registry = ResonantFieldRegistry.get(serverLevel.server)
        val field = registry.get(id)
        registry.remove(id)
        field?.markers()?.forEach { markerPos ->
            if (markerPos != worldPosition) {
                (serverLevel.getBlockEntity(markerPos) as? FieldMarkerBlockEntity)?.clearField()
            }
        }
        fieldId = null
        syncLit()
        setChanged()
    }

    fun clearField() {
        fieldId = null
        syncLit()
        setChanged()
    }

    private fun syncLit() {
        val state = blockState
        val tuned = fieldId != null
        if (state.hasProperty(HexwrightBlockStates.ACTIVE) && state.getValue(HexwrightBlockStates.ACTIVE) != tuned) {
            level?.setBlock(worldPosition, state.setValue(HexwrightBlockStates.ACTIVE, tuned), 3)
        }
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        fieldId = if (tag.hasUUID("FieldId")) tag.getUUID("FieldId") else null
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        fieldId?.let { tag.putUUID("FieldId", it) }
    }
}
