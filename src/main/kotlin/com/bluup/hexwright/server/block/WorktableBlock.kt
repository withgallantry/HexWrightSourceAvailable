package com.bluup.hexwright.server.block

import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class WorktableBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = SHAPE

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return WorktableBlockEntity(pos, state)
    }

    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val be = level.getBlockEntity(pos) as? WorktableBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME
        BlockEntityUIFactory.INSTANCE.openUI(be, serverPlayer)
        return InteractionResult.CONSUME
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            val be = level.getBlockEntity(pos) as? WorktableBlockEntity
            if (be != null) {
                Containers.dropContents(level, pos, be)
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
