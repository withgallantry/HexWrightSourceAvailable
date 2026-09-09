package com.bluup.hexwright.server.block

import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory
import com.bluup.hexwright.server.progression.Mastery
import com.bluup.hexwright.server.network.EssenceNetwork
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
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
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class CrucibleBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 10.0, 16.0)
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
        return CrucibleBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (type != HexwrightBlocks.CRUCIBLE_BLOCK_ENTITY) {
            return null
        }
        val ticker = BlockEntityTicker<CrucibleBlockEntity> { tickLevel, _, _, be ->
            if (tickLevel.isClientSide) {
                be.clientTick()
            } else {
                be.serverTick()
            }
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

        val be = level.getBlockEntity(pos) as? CrucibleBlockEntity ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.CONSUME
        be.noteOpenedBy(serverPlayer)
        Mastery.checkEssenceMilestones(
            serverPlayer,
            EssenceNetwork.resolve(be.getItem(CrucibleBlockEntity.POUCH_SLOT), level, pos)
        )
        BlockEntityUIFactory.INSTANCE.openUI(be, serverPlayer)
        return InteractionResult.CONSUME
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if (!state.getValue(HexwrightBlockStates.ACTIVE)) {
            return
        }
        if (random.nextInt(6) == 0) {
            level.playLocalSound(
                pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                0.6f, 1.0f, false
            )
        }
        val x = pos.x + 0.3 + random.nextDouble() * 0.4
        val z = pos.z + 0.3 + random.nextDouble() * 0.4
        level.addParticle(ParticleTypes.WITCH, x, pos.y + 0.675, z, 0.0, 0.02, 0.0)
    }

    @Suppress("DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            val be = level.getBlockEntity(pos) as? CrucibleBlockEntity
            if (be != null) {
                Containers.dropContents(level, pos, be)
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
