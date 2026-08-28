package com.bluup.hexwright.server.block

import com.bluup.hexwright.server.crucible.EssencePouchData
import com.bluup.hexwright.server.harmonic.HarmonicSubscriptions
import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.network.ResonanceNames
import com.bluup.hexwright.server.network.ResonantAttunement
import com.bluup.hexwright.server.network.ResonantKeyItem
import com.bluup.hexwright.server.network.ResonantRingItem
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
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
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class ResonanceTowerBlock(properties: Properties) : Block(properties), EntityBlock {

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
    ): VoxelShape = Shapes.block()

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return ResonanceTowerBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (type != HexwrightBlocks.RESONANCE_TOWER_BLOCK_ENTITY || !level.isClientSide) {
            return null
        }
        val ticker = BlockEntityTicker<ResonanceTowerBlockEntity> { _, _, _, be -> be.clientTick() }
        @Suppress("UNCHECKED_CAST")
        return ticker as BlockEntityTicker<T>
    }

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)
        val server = level.server ?: return
        ResonanceNames.nameOrAssign(server, ResonantAttunement.networkKey(level, pos))
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
        val be = level.getBlockEntity(pos) as? ResonanceTowerBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME
        val server = level.server ?: return InteractionResult.CONSUME
        val held = player.getItemInHand(hand)

        val networkName = ResonanceNames.nameOrAssign(server, ResonantAttunement.networkKey(level, pos))

        when {
            held.item is EndlessPouchItem -> {
                val previous = be.dockPouch(held.copy())
                player.setItemInHand(hand, previous)
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f)
                serverPlayer.displayClientMessage(
                    Component.translatable("message.hexwright.tower.docked").withStyle(ChatFormatting.AQUA), true
                )
            }
            held.item is ResonantKeyItem -> {
                ResonantKeyItem.attune(held, level, pos)
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.5f)
                serverPlayer.displayClientMessage(
                    Component.translatable("message.hexwright.tower.attuned", networkName, be.radius().toInt())
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true
                )
            }
            held.item is ResonantRingItem -> {
                ResonantRingItem.attune(held, level, pos)
                HarmonicSubscriptions.refresh(serverPlayer)
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.8f)
                serverPlayer.displayClientMessage(
                    Component.translatable("message.hexwright.tower.ring_attuned", networkName)
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true
                )
            }
            held.isEmpty -> {
                val pouch = be.dockPouch(ItemStack.EMPTY)
                if (!pouch.isEmpty) {
                    player.setItemInHand(hand, pouch)
                    serverPlayer.displayClientMessage(
                        Component.translatable("message.hexwright.tower.undocked").withStyle(ChatFormatting.YELLOW), true
                    )
                } else {
                    serverPlayer.displayClientMessage(
                        Component.translatable("message.hexwright.tower.empty").withStyle(ChatFormatting.GRAY), true
                    )
                }
            }
            else -> {
                val pouch = be.dockedPouch()
                val status = if (pouch.item is EndlessPouchItem) {
                    Component.translatable(
                        "message.hexwright.tower.status",
                        networkName,
                        EndlessPouchItem.formatAmount(EssencePouchData.total(pouch)),
                        be.radius().toInt()
                    ).withStyle(ChatFormatting.AQUA)
                } else {
                    Component.translatable("message.hexwright.tower.empty_named", networkName)
                        .withStyle(ChatFormatting.GRAY)
                }
                serverPlayer.displayClientMessage(status, true)
            }
        }
        return InteractionResult.CONSUME
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            val be = level.getBlockEntity(pos) as? ResonanceTowerBlockEntity
            if (be != null) {
                Containers.dropContents(level, pos, be)
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
