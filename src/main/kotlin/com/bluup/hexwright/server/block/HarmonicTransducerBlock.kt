package com.bluup.hexwright.server.block

import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class HarmonicTransducerBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        val FACING: DirectionProperty = BlockStateProperties.FACING

        private val SHAPE_UP: VoxelShape = box(5.0, 0.0, 5.0, 11.0, 3.0, 11.0)
        private val SHAPE_DOWN: VoxelShape = box(5.0, 13.0, 5.0, 11.0, 16.0, 11.0)
        private val SHAPE_NORTH: VoxelShape = box(5.0, 5.0, 13.0, 11.0, 11.0, 16.0)
        private val SHAPE_SOUTH: VoxelShape = box(5.0, 5.0, 0.0, 11.0, 11.0, 3.0)
        private val SHAPE_WEST: VoxelShape = box(13.0, 5.0, 5.0, 16.0, 11.0, 11.0)
        private val SHAPE_EAST: VoxelShape = box(0.0, 5.0, 5.0, 3.0, 11.0, 11.0)

        fun watchedPos(pos: BlockPos, state: BlockState): BlockPos =
            pos.relative(state.getValue(FACING).opposite)
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(HexwrightBlockStates.ACTIVE, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, HexwrightBlockStates.ACTIVE)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val state = defaultBlockState()
            .setValue(FACING, context.clickedFace)
            .setValue(HexwrightBlockStates.ACTIVE, false)
        return if (state.canSurvive(context.level, context.clickedPos)) state else null
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val watched = level.getBlockState(watchedPos(pos, state))
        return !watched.isAir && watched.block !is LiquidBlock
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        if (direction == state.getValue(FACING).opposite && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState()
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = when (state.getValue(FACING)) {
        Direction.DOWN -> SHAPE_DOWN
        Direction.NORTH -> SHAPE_NORTH
        Direction.SOUTH -> SHAPE_SOUTH
        Direction.WEST -> SHAPE_WEST
        Direction.EAST -> SHAPE_EAST
        else -> SHAPE_UP
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        HarmonicTransducerBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide || type != HexwrightBlocks.HARMONIC_TRANSDUCER_BLOCK_ENTITY) {
            return null
        }
        val ticker = BlockEntityTicker<HarmonicTransducerBlockEntity> { _, _, _, be -> be.serverTick() }
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
        val be = level.getBlockEntity(pos) as? HarmonicTransducerBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME

        val held = player.getItemInHand(hand)
        if (!held.isEmpty && be.tryChargeFrom(held, serverPlayer)) {
            return InteractionResult.CONSUME
        }

        BlockEntityUIFactory.INSTANCE.openUI(be, serverPlayer)
        return InteractionResult.CONSUME
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block && !level.isClientSide) {
            (level.getBlockEntity(pos) as? HarmonicTransducerBlockEntity)?.dropAllOnBreak()
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
