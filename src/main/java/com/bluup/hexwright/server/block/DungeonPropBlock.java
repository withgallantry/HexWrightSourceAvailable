package com.bluup.hexwright.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DungeonPropBlock extends Block implements EntityBlock {

    public static final BooleanProperty WHOLE = BooleanProperty.create("whole");

    private final DungeonProp prop;

    public DungeonPropBlock(Properties properties, DungeonProp prop) {
        super(properties);
        this.prop = prop;
        registerDefaultState(getStateDefinition().any()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
            .setValue(WHOLE, false));
    }

    public DungeonProp prop() {
        return this.prop;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, WHOLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = this.prop.stance() == DungeonProp.Stance.WALL && clicked.getAxis().isHorizontal()
            ? clicked
            : context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        DungeonPropCollision.Placement placement = DungeonPropCollision.Placement.byHand(this.prop, facing);
        boolean standing = this.prop.stance() != DungeonProp.Stance.CEILING;
        for (Map.Entry<BlockPos, VoxelShape> cell : placement.cells().entrySet()) {
            BlockPos offset = cell.getKey();
            if (offset.equals(BlockPos.ZERO)) {
                continue;
            }
            BlockPos at = pos.offset(offset);
            if (standing && offset.getY() == -1 && !level.getBlockState(at).canBeReplaced()) {
                continue;
            }
            if (level.isOutsideBuildHeight(at)
                || !level.getWorldBorder().isWithinBounds(at)
                || !level.getBlockState(at).canBeReplaced()
                || !level.isUnobstructed(null, cell.getValue().move(at.getX(), at.getY(), at.getZ()))) {
                if (!level.isClientSide && context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(
                        Component.translatable("block.hexwright.prop.no_room", getName()), true);
                }
                return null;
            }
        }
        return defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, facing)
            .setValue(WHOLE, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (state.getValue(WHOLE) && level.getBlockEntity(pos) instanceof DungeonPropBlockEntity entity) {
            entity.placeByHand();
            if (!level.isClientSide) {
                placeParts(level, pos, entity.placement());
            }
        }
    }

    private static void grow(Level level, BlockPos pos, BlockState state, DungeonPropBlockEntity entity) {
        if (state.getValue(WHOLE)) {
            return;
        }
        DungeonPropCollision.Placement placement = entity.placement();
        for (BlockPos offset : placement.cells().keySet()) {
            if (!level.isLoaded(pos.offset(offset))) {
                return;
            }
        }
        level.setBlock(pos, state.setValue(WHOLE, true), Block.UPDATE_ALL);
        placeParts(level, pos, placement);
    }

    private static void placeParts(Level level, BlockPos pos, DungeonPropCollision.Placement placement) {
        for (BlockPos offset : placement.cells().keySet()) {
            BlockPos at = pos.offset(offset);
            if (offset.equals(BlockPos.ZERO)
                || level.isOutsideBuildHeight(at)
                || !level.getBlockState(at).canBeReplaced()) {
                continue;
            }
            level.setBlock(at, DungeonPropParts.BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof DungeonPropPartBlockEntity part) {
                part.bind(placement, offset);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && state.getValue(WHOLE)
            && level.getBlockEntity(pos) instanceof DungeonPropBlockEntity entity) {
            for (BlockPos offset : entity.placement().cells().keySet()) {
                BlockPos at = pos.offset(offset);
                if (!offset.equals(BlockPos.ZERO)
                    && level.getBlockEntity(at) instanceof DungeonPropPartBlockEntity part
                    && part.master().equals(pos)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        DungeonPropCollision.Placement placement = level.getBlockEntity(pos) instanceof DungeonPropBlockEntity entity
            ? entity.placement()
            : DungeonPropCollision.Placement.byHand(this.prop, state.getValue(HorizontalDirectionalBlock.FACING));
        return placement.cell(BlockPos.ZERO);
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

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide || state.getValue(WHOLE) || type != DungeonProp.BLOCK_ENTITY) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<DungeonPropBlockEntity>) DungeonPropBlock::grow;
    }
}
