package com.bluup.hexwright.server.block

import com.bluup.hexwright.server.crucible.EssencePouchData
import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.network.EssenceNetwork
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class EssenceGaugeBlock(properties: Properties) : Block(properties), EntityBlock {

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = Shapes.block()

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return EssenceGaugeBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide || type != HexwrightBlocks.ESSENCE_GAUGE_BLOCK_ENTITY) {
            return null
        }
        val ticker = BlockEntityTicker<EssenceGaugeBlockEntity> { _, _, _, be ->
            be.serverTick()
        }
        @Suppress("UNCHECKED_CAST")
        return ticker as BlockEntityTicker<T>
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        val be = level.getBlockEntity(pos) as? EssenceGaugeBlockEntity ?: return 0
        return be.signal()
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
        val be = level.getBlockEntity(pos) as? EssenceGaugeBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME
        val held = player.getItemInHand(hand)

        when {
            EssenceNetwork.isEssenceSource(held) -> {
                val previous = be.dockSource(held.copyWithCount(1))
                held.shrink(1)
                if (!previous.isEmpty) {
                    if (held.isEmpty) {
                        player.setItemInHand(hand, previous)
                    } else {
                        player.inventory.placeItemBackInInventory(previous)
                    }
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.7f, 1.2f)
                EssenceNetwork.warnIfOutOfRange(serverPlayer, be.dockedSource(), level, pos)
            }
            held.isEmpty && player.isShiftKeyDown -> {
                val source = be.dockSource(ItemStack.EMPTY)
                if (!source.isEmpty) {
                    player.setItemInHand(hand, source)
                }
            }
            held.isEmpty -> {
                val aspect = be.cycleAspect()
                val pouch = EssenceNetwork.resolve(be.dockedSource(), level, pos)
                val amount = if (pouch.item is EndlessPouchItem) EssencePouchData.get(pouch, aspect) else 0.0
                serverPlayer.displayClientMessage(
                    Component.translatable(
                        "message.hexwright.gauge.aspect",
                        EndlessPouchItem.aspectName(aspect),
                        EndlessPouchItem.formatAmount(amount),
                        be.signal()
                    ).withStyle(ChatFormatting.AQUA),
                    true
                )
            }
        }
        return InteractionResult.CONSUME
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            val be = level.getBlockEntity(pos) as? EssenceGaugeBlockEntity
            if (be != null) {
                Containers.dropContents(level, pos, be)
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
