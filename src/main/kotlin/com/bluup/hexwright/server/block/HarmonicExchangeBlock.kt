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
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult

@Suppress("OVERRIDE_DEPRECATION")
class HarmonicExchangeBlock(properties: Properties) : Block(properties), EntityBlock {

    init {
        registerDefaultState(stateDefinition.any().setValue(HexwrightBlockStates.ACTIVE, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HexwrightBlockStates.ACTIVE)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(HexwrightBlockStates.ACTIVE, false)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        HarmonicExchangeBlockEntity(pos, state)

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
        val be = level.getBlockEntity(pos) as? HarmonicExchangeBlockEntity ?: return InteractionResult.PASS
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
                    Component.translatable("message.hexwright.harmonic_exchange.key_unattuned")
                        .withStyle(ChatFormatting.RED)
                !be.inTowerRange() ->
                    Component.translatable(
                        "message.hexwright.harmonic_exchange.out_of_range", EssenceNetwork.keyRange()
                    ).withStyle(ChatFormatting.RED)
                else ->
                    Component.translatable("message.hexwright.harmonic_exchange.active")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
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
            val be = level.getBlockEntity(pos) as? HarmonicExchangeBlockEntity
            be?.onRemovedFromWorld()
            be?.dropAllOnBreak()
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
