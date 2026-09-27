package com.bluup.hexwright.server.block;

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

public class DungeonPropPartBlock extends Block implements EntityBlock {

    public DungeonPropPartBlock(Properties properties) {
        super(properties);
    }

    private static VoxelShape shapeAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DungeonPropPartBlockEntity part
            ? part.shape()
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
        if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof DungeonPropPartBlockEntity part) {
            BlockPos master = part.master();
            if (level.getBlockState(master).getBlock() instanceof DungeonPropBlock) {
                level.destroyBlock(master, false);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof DungeonPropPartBlockEntity part && part.prop() != null
            ? new ItemStack(DungeonPropItems.item(part.prop()))
            : ItemStack.EMPTY;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DungeonPropPartBlockEntity(pos, state);
    }
}
