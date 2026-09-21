package com.bluup.hexwright.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class DungeonPropBlock extends Block implements EntityBlock {

    private final DungeonProp prop;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public DungeonPropBlock(Properties properties, DungeonProp prop) {
        super(properties);
        this.prop = prop;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            this.shapes.put(facing, box(prop.shape(), facing));
        }
        registerDefaultState(getStateDefinition().any()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    private static VoxelShape box(DungeonProp.Shape shape, Direction facing) {
        double west = clamp(8 - shape.side());
        double east = clamp(8 + shape.side());
        double north = clamp(8 - shape.front());
        double south = clamp(8 + shape.back());
        double top = Math.min(32, Math.max(1, shape.height()));
        if (east - west < 1 || south - north < 1) {
            return Shapes.empty();
        }
        VoxelShape drawn = Shapes.box(west / 16, 0, north / 16, east / 16, top / 16, south / 16);
        return switch (facing) {
            case SOUTH -> Shapes.box(1 - east / 16, 0, 1 - south / 16, 1 - west / 16, top / 16, 1 - north / 16);
            case WEST -> Shapes.box(north / 16, 0, 1 - east / 16, south / 16, top / 16, 1 - west / 16);
            case EAST -> Shapes.box(1 - south / 16, 0, west / 16, 1 - north / 16, top / 16, east / 16);
            default -> drawn;
        };
    }

    private static double clamp(double sixteenths) {
        return Math.max(0, Math.min(16, sixteenths));
    }

    public DungeonProp prop() {
        return this.prop;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.getOrDefault(state.getValue(HorizontalDirectionalBlock.FACING), Shapes.block());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DungeonPropBlockEntity(pos, state);
    }
}
