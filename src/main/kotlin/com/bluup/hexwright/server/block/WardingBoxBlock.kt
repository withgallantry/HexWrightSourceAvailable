package com.bluup.hexwright.server.block

import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class WardingBoxBlock(properties: Properties) : Block(properties), EntityBlock {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(HexwrightBlockStates.ACTIVE, false)
                .setValue(BlockStateProperties.TRIGGERED, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HexwrightBlockStates.ACTIVE, BlockStateProperties.TRIGGERED)
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        val be = level.getBlockEntity(pos) as? WardingBoxBlockEntity ?: return 0
        return be.mediaSignal()
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = Shapes.block()

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return WardingBoxBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide || type != HexwrightBlocks.WARDING_BOX_BLOCK_ENTITY) {
            return null
        }
        val ticker = BlockEntityTicker<WardingBoxBlockEntity> { _, _, _, be ->
            be.serverTick()
        }
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
        if (!level.isClientSide) {
            (level.getBlockEntity(pos) as? WardingBoxBlockEntity)?.initialize(placer, stack)
        }
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

        val be = level.getBlockEntity(pos) as? WardingBoxBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME

        val held = player.getItemInHand(hand)
        if (!held.isEmpty && be.tryChargeFrom(held, serverPlayer)) {
            return InteractionResult.CONSUME
        }

        BlockEntityUIFactory.INSTANCE.openUI(be, serverPlayer)
        return InteractionResult.CONSUME
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if (!state.getValue(HexwrightBlockStates.ACTIVE)) {
            return
        }
        val x = pos.x + 0.2 + random.nextDouble() * 0.6
        val z = pos.z + 0.2 + random.nextDouble() * 0.6
        level.addParticle(ParticleTypes.ENCHANT, x, pos.y + 1.1, z, 0.0, 0.35, 0.0)
    }

    override fun getCloneItemStack(level: BlockGetter, pos: BlockPos, state: BlockState): ItemStack {
        val be = level.getBlockEntity(pos) as? WardingBoxBlockEntity ?: return super.getCloneItemStack(level, pos, state)
        return be.toItemStack()
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            val be = level.getBlockEntity(pos) as? WardingBoxBlockEntity
            if (be != null && !level.isClientSide) {
                Containers.dropItemStack(
                    level,
                    pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                    be.toItemStack()
                )
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
