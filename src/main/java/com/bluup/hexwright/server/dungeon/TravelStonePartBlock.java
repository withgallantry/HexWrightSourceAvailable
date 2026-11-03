package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TravelStonePartBlock extends Block implements EntityBlock, LiquidBlockContainer {

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
            ? stone.collision().cell(stone.front(), stone.phase(), pos.subtract(stonePos))
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
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TravelStonePartBlockEntity part
            && level.getBlockState(part.stone()).getBlock() instanceof TravelStoneBlock) {
            level.destroyBlock(part.stone(), false);
        }
        super.playerWillDestroy(level, pos, state, player);
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
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof TravelStonePartBlockEntity part
            ? new ItemStack(level.getBlockState(part.stone()).getBlock())
            : ItemStack.EMPTY;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TravelStonePartBlockEntity(pos, state);
    }
}
