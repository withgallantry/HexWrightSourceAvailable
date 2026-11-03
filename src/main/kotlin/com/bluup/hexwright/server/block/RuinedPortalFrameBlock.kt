package com.bluup.hexwright.server.block

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class RuinedPortalFrameBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        private val OUTLINE: VoxelShape = box(0.0, 4.0, 6.0, 16.0, 16.0, 10.0)
        private val COLLISION: VoxelShape = Shapes.empty()

        private const val BREAK_HARDNESS = 150.0f

        private const val MIN_BREAK_TICKS = 30 * 20
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = OUTLINE

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = COLLISION

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun getDestroyProgress(state: BlockState, player: Player, level: BlockGetter, pos: BlockPos): Float {
        val progress = player.getDestroySpeed(state) / BREAK_HARDNESS / 30.0f
        return minOf(progress, 1.0f / MIN_BREAK_TICKS)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return RuinedPortalFrameBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (type != HexwrightBlocks.RUINED_PORTAL_FRAME_BLOCK_ENTITY) {
            return null
        }
        val ticker = BlockEntityTicker<RuinedPortalFrameBlockEntity> { tickLevel, _, _, be ->
            if (tickLevel.isClientSide) be.clientTick() else be.serverTick()
        }
        @Suppress("UNCHECKED_CAST")
        return ticker as BlockEntityTicker<T>
    }
}
