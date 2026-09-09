package com.bluup.hexwright.server.block

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class VaultPlinthBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        private val SHAPE: VoxelShape = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0)
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = SHAPE

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return VaultPlinthBlockEntity(pos, state)
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
        val plinth = level.getBlockEntity(pos) as? VaultPlinthBlockEntity ?: return InteractionResult.PASS
        val handled = if (plinth.displayed.isEmpty) {
            plinth.place(player, player.getItemInHand(hand))
        } else {
            plinth.claim(player)
        }
        if (!handled) {
            return InteractionResult.PASS
        }
        level.playSound(
            null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7f,
            if (plinth.displayed.isEmpty) 0.9f else 1.2f
        )
        return InteractionResult.CONSUME
    }
}
