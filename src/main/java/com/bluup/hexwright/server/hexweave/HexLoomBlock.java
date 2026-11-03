package com.bluup.hexwright.server.hexweave;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class HexLoomBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final double DOCK_TOP = 16 / 16.0;

    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    public HexLoomBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HexLoomBlockEntity loom)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        if (loom.orb().isEmpty()) {
            if (!(held.getItem() instanceof WovenOrbItem)) {
                if (hand == InteractionHand.MAIN_HAND && !level.isClientSide
                    && !(player.getOffhandItem().getItem() instanceof WovenOrbItem)) {
                    player.displayClientMessage(Component.translatable("message.hexwright.hex_loom.empty")
                        .withStyle(ChatFormatting.GRAY), true);
                }
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                loom.setOrb(held.split(1));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (onTop(pos, hit)) {
            if (!level.isClientSide) {
                ItemStack orb = loom.orb();
                loom.setOrb(ItemStack.EMPTY);
                if (held.isEmpty()) {
                    player.setItemInHand(hand, orb);
                } else if (!player.getInventory().add(orb)) {
                    player.drop(orb, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (player instanceof ServerPlayer server) {
            HexLoomEditing.open(server, loom);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean onTop(BlockPos pos, BlockHitResult hit) {
        return hit.getDirection() == Direction.UP || hit.getLocation().y - pos.getY() > 1 - 1e-3;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HexLoomBlockEntity loom) {
            loom.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HexLoomBlockEntity(pos, state);
    }

    private static Map<Direction, VoxelShape> shapes() {
        VoxelShape facingZ = Block.box(0.5, 0, 2.5, 15.5, 16, 13.5);
        VoxelShape facingX = Block.box(2.5, 0, 0.5, 13.5, 16, 15.5);
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, facing.getAxis() == Direction.Axis.Z ? facingZ : facingX);
        }
        return shapes;
    }
}
