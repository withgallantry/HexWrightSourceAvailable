package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CrystalAltarBlock extends Block implements EntityBlock {

    public CrystalAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DungeonKeyItem key) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            return TravelStones.useKey((ServerLevel) level, pos, (ServerPlayer) player, held,
                key.draw(level.random));
        }
        if (!held.isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CrystalAltarBlockEntity altar && altar.lit()) {
            return TravelStones.takeKey((ServerLevel) level, altar, (ServerPlayer) player);
        }
        player.displayClientMessage(Component.translatable("message.hexwright.travel_stone.altar_hint"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrystalAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide || type != InstancedDungeons.CRYSTAL_ALTAR_BLOCK_ENTITY) {
            return null;
        }
        BlockEntityTicker<CrystalAltarBlockEntity> ticker =
            (tickLevel, pos, tickState, altar) -> altar.serverTick((ServerLevel) tickLevel);
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> cast = (BlockEntityTicker<T>) ticker;
        return cast;
    }
}
