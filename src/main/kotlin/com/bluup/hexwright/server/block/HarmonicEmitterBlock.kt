package com.bluup.hexwright.server.block

import com.bluup.hexwright.server.network.EssenceNetwork
import com.bluup.hexwright.server.network.ResonantKeyItem
import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
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
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class HarmonicEmitterBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        private val SHAPE: VoxelShape = box(4.0, 0.0, 4.0, 12.0, 13.0, 12.0)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(HexwrightBlockStates.ACTIVE, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HexwrightBlockStates.ACTIVE)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(HexwrightBlockStates.ACTIVE, false)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = SHAPE

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        HarmonicEmitterBlockEntity(pos, state)

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
        val be = level.getBlockEntity(pos) as? HarmonicEmitterBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME
        val held = player.getItemInHand(hand)

        if (held.item is ResonantKeyItem) {
            val previous = be.dockKey(held.copyWithCount(1))
            held.shrink(1)
            if (!previous.isEmpty) {
                player.inventory.placeItemBackInInventory(previous)
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f)
            val message = when {
                be.networkKey() == null ->
                    Component.translatable("message.hexwright.harmonic_emitter.key_unattuned")
                        .withStyle(ChatFormatting.RED)
                !be.inTowerRange() ->
                    Component.translatable(
                        "message.hexwright.harmonic_emitter.out_of_range", EssenceNetwork.keyRange()
                    ).withStyle(ChatFormatting.RED)
                else ->
                    Component.translatable("message.hexwright.harmonic_emitter.keyed")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
            }
            serverPlayer.displayClientMessage(message, true)
            return InteractionResult.CONSUME
        }

        if (!held.isEmpty && be.tryChargeFrom(held, serverPlayer)) {
            return InteractionResult.CONSUME
        }

        BlockEntityUIFactory.INSTANCE.openUI(be, serverPlayer)
        return InteractionResult.CONSUME
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block && !level.isClientSide) {
            (level.getBlockEntity(pos) as? HarmonicEmitterBlockEntity)?.dropAllOnBreak()
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
