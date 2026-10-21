package com.bluup.hexwright.server.cloak;

import com.bluup.hexwright.server.block.HexwrightBlockStates;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.fluid.HexidPipeBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class ShieldGeneratorBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.or(
        box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0),
        box(3.0, 12.0, 5.0, 13.0, 15.0, 11.0));

    public ShieldGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HexwrightBlockStates.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HexwrightBlockStates.ACTIVE);
    }

    public static Direction[] ports(BlockState state) {
        Direction facing = state.getValue(FACING);
        return new Direction[] {facing.getClockWise(), facing.getCounterClockWise()};
    }

    public static boolean joins(BlockState state, Direction side) {
        return side.getAxis() == state.getValue(FACING).getClockWise().getAxis();
    }

    public static boolean switchedOff(Level level, BlockPos pos) {
        if (level.hasNeighborSignal(pos)) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).isSignalSource()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos,
                                boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ShieldGeneratorBlockEntity generator) {
            generator.neighborChanged(server);
        }
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShieldGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide || type != HexwrightBlocks.SHIELD_GENERATOR_BLOCK_ENTITY) {
            return null;
        }
        BlockEntityTicker<ShieldGeneratorBlockEntity> ticker = (l, p, s, be) -> be.serverTick((ServerLevel) l);
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> cast = (BlockEntityTicker<T>) ticker;
        return cast;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ShieldGeneratorBlockEntity generator) {
            report(player, (ServerLevel) level, generator);
        }
        return InteractionResult.CONSUME;
    }

    private static void report(Player player, ServerLevel level, ShieldGeneratorBlockEntity generator) {
        if (generator.field() == null) {
            say(player, "hexwright.shield_generator.status.unwritten");
            return;
        }
        if (generator.obeysRedstone() && switchedOff(level, generator.getBlockPos())) {
            say(player, "hexwright.shield_generator.status.switched_off");
            return;
        }
        ShieldGeneratorBlockEntity.Feed feed = generator.feed();
        switch (feed.status()) {
            case UNFED -> say(player, "hexwright.shield_generator.status.unfed");
            case FED -> say(player, generator.isUp(level)
                    ? "hexwright.shield_generator.status.running"
                    : "hexwright.shield_generator.status.starting",
                String.format(Locale.ROOT, "%.1f", feed.hoursLeft()));
        }
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
            && level.getBlockEntity(pos) instanceof ShieldGeneratorBlockEntity generator) {
            generator.shutDown(server);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
