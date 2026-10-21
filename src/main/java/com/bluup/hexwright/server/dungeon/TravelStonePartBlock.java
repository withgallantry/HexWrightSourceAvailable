package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TravelStonePartBlock extends Block implements EntityBlock {

    public TravelStonePartBlock(Properties properties) {
        super(properties);
    }

    private static VoxelShape shapeAt(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TravelStonePartBlockEntity part)) {
            return Shapes.empty();
        }
        BlockPos stonePos = part.stone();
        if (level instanceof Level world && !TravelStoneCollision.chunkReady(world, stonePos)) {
            return Shapes.empty();
        }
        return level.getBlockEntity(stonePos) instanceof TravelStoneBlockEntity stone
            ? TravelStoneCollision.cell(stone.front(), stone.phase(), pos.subtract(stonePos))
            : Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeAt(level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeAt(level, pos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TravelStonePartBlockEntity part
            && level.getBlockState(part.stone()).getBlock() instanceof TravelStoneBlock) {
            level.destroyBlock(part.stone(), false);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(InstancedDungeons.TRAVEL_STONE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TravelStonePartBlockEntity(pos, state);
    }
}
