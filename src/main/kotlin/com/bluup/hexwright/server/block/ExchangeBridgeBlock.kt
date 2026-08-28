package com.bluup.hexwright.server.block

import com.bluup.hexwright.server.network.ResonantKeyItem
import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class ExchangeBridgeBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        val FACING: DirectionProperty = BlockStateProperties.HORIZONTAL_FACING

        private val SHAPE_NORTH_SOUTH: VoxelShape = box(0.0, 0.0, 3.0, 16.0, 9.0, 13.0)
        private val SHAPE_EAST_WEST: VoxelShape = box(3.0, 0.0, 0.0, 13.0, 9.0, 16.0)
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HexwrightBlockStates.ACTIVE, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, HexwrightBlockStates.ACTIVE)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState()
            .setValue(FACING, context.horizontalDirection.opposite)
            .setValue(HexwrightBlockStates.ACTIVE, false)


    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape =
        if (state.getValue(FACING).axis == Direction.Axis.X) SHAPE_EAST_WEST else SHAPE_NORTH_SOUTH

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        ExchangeBridgeBlockEntity(pos, state)

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
        val be = level.getBlockEntity(pos) as? ExchangeBridgeBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME
        val held = player.getItemInHand(hand)

        if (held.item is ResonantKeyItem && be.dockKey(held.copyWithCount(1))) {
            held.shrink(1)
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f)
            val refusal = be.tuningRefusal()
            val message = when (refusal) {
                null -> Component.translatable("message.hexwright.exchange_bridge.keyed")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                "hexwright_bridge_unkeyed" -> Component.translatable("message.hexwright.exchange_bridge.needs_keys")
                    .withStyle(ChatFormatting.YELLOW)
                "hexwright_bridge_same_network" ->
                    Component.translatable("message.hexwright.exchange_bridge.same_network")
                        .withStyle(ChatFormatting.RED)
                else -> Component.translatable("message.hexwright.exchange_bridge.key_unattuned")
                    .withStyle(ChatFormatting.RED)
            }
            serverPlayer.displayClientMessage(message, true)
            return InteractionResult.CONSUME
        }

        BlockEntityUIFactory.INSTANCE.openUI(be, serverPlayer)
        return InteractionResult.CONSUME
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block && !level.isClientSide) {
            val be = level.getBlockEntity(pos) as? ExchangeBridgeBlockEntity
            be?.onRemovedFromWorld()
            be?.dropAllOnBreak()
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
