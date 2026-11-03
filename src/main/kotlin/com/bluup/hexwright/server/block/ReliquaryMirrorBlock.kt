package com.bluup.hexwright.server.block

import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class ReliquaryMirrorBlock(properties: Properties) : Block(properties), EntityBlock {

    init {
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.horizontalDirection.opposite)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.ENTITYBLOCK_ANIMATED

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0)

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return ReliquaryMirrorBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (type != HexwrightBlocks.RELIQUARY_MIRROR_BLOCK_ENTITY) {
            return null
        }
        val ticker = BlockEntityTicker<ReliquaryMirrorBlockEntity> { tickLevel, _, _, be ->
            be.tickChestAnimation(tickLevel)
        }
        @Suppress("UNCHECKED_CAST")
        return ticker as BlockEntityTicker<T>
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
        val be = level.getBlockEntity(pos) as? ReliquaryMirrorBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME
        be.onOpenedByPlayer()
        BlockEntityUIFactory.INSTANCE.openUI(be, serverPlayer)
        return InteractionResult.CONSUME
    }

    @Suppress("DEPRECATION")
    override fun triggerEvent(state: BlockState, level: Level, pos: BlockPos, id: Int, param: Int): Boolean {
        val handledByBlock = super.triggerEvent(state, level, pos, id, param)
        val be = level.getBlockEntity(pos)
        return handledByBlock || (be?.triggerEvent(id, param) == true)
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            val be = level.getBlockEntity(pos) as? ReliquaryMirrorBlockEntity
            if (be != null) {
                Containers.dropContents(level, pos, be)
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
