package com.bluup.hexwright.server.block;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PlacedBottleBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final float MODEL_SCALE = 0.78f;

    private record Stand(VoxelShape outline, VoxelShape collision) {
    }

    private static final Stand CRUDE = new Stand(
        Block.box(6.44, 0.0, 6.44, 9.56, 9.36, 9.56),
        Block.box(6.44, 0.0, 6.44, 9.56, 9.36, 9.56));
    private static final Stand SOUND = new Stand(
        Block.box(4.10, 0.0, 4.10, 11.90, 10.14, 11.90),
        Block.box(4.10, 0.0, 4.10, 11.90, 10.14, 11.90));
    private static final Stand FINE = new Stand(
        Block.box(2.54, 0.0, 2.54, 13.46, 10.92, 13.46),
        Block.box(4.10, 0.0, 4.10, 11.90, 10.92, 11.90));
    private static final Stand EXQUISITE = new Stand(
        Block.box(0.20, 0.0, 0.20, 15.80, 14.82, 15.80),
        Block.box(4.49, 0.0, 4.49, 11.51, 14.82, 11.51));
    private static final Stand MASTERWORK = new Stand(
        Block.box(3.77, 0.0, 3.77, 12.23, 14.23, 12.23),
        Block.box(5.26, 0.0, 5.26, 10.74, 11.90, 10.74));

    private static Stand stand(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> CRUDE;
            case SOUND -> SOUND;
            case FINE -> FINE;
            case EXQUISITE -> EXQUISITE;
            case MASTERWORK -> MASTERWORK;
        };
    }

    public PlacedBottleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(ROTATION, 0)
            .setValue(WATERLOGGED, Boolean.FALSE));
    }

    public static boolean place(Level level, BlockPos pos, ItemStack bottle, float placerYRot,
                                @Nullable Player player) {
        boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;
        BlockState state = HexwrightBlocks.PLACED_BOTTLE_BLOCK.defaultBlockState()
            .setValue(ROTATION, Mth.floor((placerYRot + 180.0f) * 16.0f / 360.0f + 0.5f) & 15)
            .setValue(WATERLOGGED, waterlogged);

        if (!state.canSurvive(level, pos) || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof PlacedBottleBlockEntity placed) {
            ItemStack one = bottle.copy();
            one.setCount(1);
            placed.setBottle(one);
        }

        SoundType sound = state.getSoundType();
        level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, WATERLOGGED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlacedBottleBlockEntity(pos, state);
    }

    private static PocketCasterData.Quality gradeAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlacedBottleBlockEntity placed
            ? placed.grade()
            : PocketCasterData.Quality.CRUDE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return stand(gradeAt(level, pos)).outline();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return stand(gradeAt(level, pos)).collision();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
            ? Fluids.WATER.getSource(false)
            : super.getFluidState(state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlacedBottleBlockEntity placed) || placed.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack bottle = placed.takeBottle();
        if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }
        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0f) / 4.0f, sound.getPitch());
        level.removeBlock(pos, false);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof PlacedBottleBlockEntity placed
            ? placed.getBottle().copy()
            : ItemStack.EMPTY;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof PlacedBottleBlockEntity placed && !placed.isEmpty()) {
                Block.popResource(level, pos, placed.takeBottle());
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
