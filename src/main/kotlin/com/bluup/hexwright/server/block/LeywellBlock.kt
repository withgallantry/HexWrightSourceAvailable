package com.bluup.hexwright.server.block

import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.network.EssenceNetwork
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
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
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class LeywellBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(HexwrightBlockStates.ACTIVE, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HexwrightBlockStates.ACTIVE)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = SHAPE

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return LeywellBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide || type != HexwrightBlocks.LEYWELL_BLOCK_ENTITY) {
            return null
        }
        val ticker = BlockEntityTicker<LeywellBlockEntity> { _, _, _, be ->
            be.serverTick()
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
        val be = level.getBlockEntity(pos) as? LeywellBlockEntity ?: return InteractionResult.PASS
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
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.7f, 1.1f)
                EssenceNetwork.warnIfOutOfRange(
                    serverPlayer, be.getItem(LeywellBlockEntity.SOURCE_SLOT), level, pos
                )
            }
            held.isEmpty && player.isShiftKeyDown -> {
                val source = be.dockSource(ItemStack.EMPTY)
                if (!source.isEmpty) {
                    player.setItemInHand(hand, source)
                }
            }
            held.isEmpty -> {
                val attunement = LeywellBlockEntity.attunement(level, pos)
                val saturation = LeywellBlockEntity.saturationCount(level, pos)
                val listing = attunement.entries.joinToString(", ") { (aspect, amount) ->
                    "${EndlessPouchItem.formatAmount(amount / saturation)} ${EndlessPouchItem.aspectName(aspect).string}"
                }
                val message = if (saturation > 1) {
                    Component.translatable(
                        "message.hexwright.leywell.attunement.shared",
                        listing, LeywellBlockEntity.WORK_INTERVAL / 20L, saturation
                    )
                } else {
                    Component.translatable(
                        "message.hexwright.leywell.attunement",
                        listing, LeywellBlockEntity.WORK_INTERVAL / 20L
                    )
                }
                serverPlayer.displayClientMessage(message.withStyle(ChatFormatting.AQUA), true)
            }
        }
        return InteractionResult.CONSUME
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if (!state.getValue(HexwrightBlockStates.ACTIVE)) {
            return
        }
        level.addParticle(
            ParticleTypes.END_ROD,
            pos.x + 0.3 + random.nextDouble() * 0.4,
            pos.y + 0.5,
            pos.z + 0.3 + random.nextDouble() * 0.4,
            0.0, 0.03, 0.0
        )
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            val be = level.getBlockEntity(pos) as? LeywellBlockEntity
            if (be != null) {
                Containers.dropContents(level, pos, be)
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
