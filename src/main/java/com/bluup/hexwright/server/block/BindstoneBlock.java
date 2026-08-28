package com.bluup.hexwright.server.block;

import com.bluup.hexwright.server.bindstone.BindstoneCube;
import com.bluup.hexwright.server.bindstone.BindstonePillar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BindstoneBlock extends BaseEntityBlock {

    public static final IntegerProperty RUNE = IntegerProperty.create("rune", 0, BindstonePillar.RUNE_COUNT);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public static final BooleanProperty CORE = BooleanProperty.create("core");

    public static final BooleanProperty HEART = BooleanProperty.create("heart");

    public BindstoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(RUNE, 0)
            .setValue(FACING, Direction.NORTH)
            .setValue(CORE, false)
            .setValue(HEART, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNE, FACING, CORE, HEART);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(CORE)) {
            return new BindstoneCoreBlockEntity(pos, state);
        }
        return state.getValue(HEART) ? new BindstoneCubeBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        if (state.getValue(CORE)) {
            return createTickerHelper(
                type,
                HexwrightBlocks.BINDSTONE_CORE_BLOCK_ENTITY,
                (world, pos, blockState, core) -> core.serverTick()
            );
        }
        if (state.getValue(HEART)) {
            return createTickerHelper(
                type,
                HexwrightBlocks.BINDSTONE_CUBE_BLOCK_ENTITY,
                (world, pos, blockState, cube) -> cube.serverTick()
            );
        }
        return null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || oldState.is(this)) {
            return;
        }
        level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos heart = BindstoneCube.findNewHeart(level, pos);
        if (heart != null) {
            level.setBlock(heart, level.getBlockState(heart).setValue(HEART, true), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean staysBindstone = newState.is(this);
        if (owns(state) && !(staysBindstone && owns(newState))) {
            level.removeBlockEntity(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !staysBindstone) {
            BindstoneCube.dissolveAround(level, pos);
        }
    }

    private static boolean owns(BlockState state) {
        return state.getValue(CORE) || state.getValue(HEART);
    }

    public static void dissolveHeart(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BindstoneBlock) || !state.getValue(HEART)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof BindstoneCubeBlockEntity cube) {
            cube.extinguish();
        }
        level.setBlock(pos, state.setValue(HEART, false), Block.UPDATE_CLIENTS);
    }

    @Override
    public InteractionResult use(
        BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        BlockPos origin = BindstonePillar.originOf(pos, state.getValue(FACING), ringOf(level, pos, state));
        BlockState core = level.getBlockState(origin);
        if (!(core.getBlock() instanceof BindstoneBlock) || !core.getValue(CORE)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(origin) instanceof BindstoneCoreBlockEntity pillar) {
            return pillar.claim(serverPlayer) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    private static int ringOf(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos middle = pos.relative(state.getValue(FACING).getOpposite());
        for (int ring = 0; ring < BindstonePillar.RINGS; ring++) {
            BlockState below = level.getBlockState(middle.below(ring));
            if (below.getBlock() instanceof BindstoneBlock && below.getValue(CORE)) {
                return ring;
            }
        }
        return 0;
    }
}
