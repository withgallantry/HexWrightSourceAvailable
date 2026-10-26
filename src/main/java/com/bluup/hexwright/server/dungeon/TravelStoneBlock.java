package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class TravelStoneBlock extends Block implements EntityBlock, LiquidBlockContainer {

    private static final VoxelShape OUTLINE = box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    static boolean placeParts(ServerLevel level, BlockPos pos, Direction facing, TravelStoneCollision collision) {
        Set<BlockPos> reach = collision.reach(facing);
        for (BlockPos offset : reach) {
            if (!TravelStoneCollision.chunkReady(level, pos.offset(offset))) {
                return false;
            }
        }
        for (BlockPos offset : reach) {
            BlockPos at = pos.offset(offset);
            if (offset.equals(BlockPos.ZERO) || level.isOutsideBuildHeight(at) || !level.getBlockState(at).canBeReplaced()) {
                continue;
            }
            level.setBlock(at, InstancedDungeons.TRAVEL_STONE_PART.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof TravelStonePartBlockEntity part) {
                part.bind(offset);
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            for (BlockPos offset : collision().reach(state.getValue(HorizontalDirectionalBlock.FACING))) {
                BlockPos at = pos.offset(offset);
                if (level.getBlockEntity(at) instanceof TravelStonePartBlockEntity part && part.stone().equals(pos)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public TravelStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    public TravelStoneCollision collision() {
        return TravelStoneCollision.TRAVEL_STONE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
            context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof TravelStoneBlockEntity stone
            ? collision().cell(stone.front(), stone.phase(), BlockPos.ZERO)
            : Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING,
            rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TravelStoneBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof TravelStoneBlockEntity stone) {
                stone.serverTick((ServerLevel) tickLevel);
            }
        };
    }
}
