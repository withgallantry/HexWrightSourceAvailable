package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.remnant.BottleData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LiquefactriumBlock extends Block implements EntityBlock {

    public static final DirectionProperty PORT = DirectionProperty.create("port", Direction.Plane.HORIZONTAL);

    public static final BooleanProperty PLUMBED = BooleanProperty.create("plumbed");

    private static final VoxelShape SHAPE = Shapes.or(
        box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
        box(1.0, 4.0, 1.0, 15.0, 10.0, 15.0),
        box(2.0, 10.0, 2.0, 14.0, 14.0, 14.0),
        box(1.0, 14.0, 1.0, 15.0, 16.0, 15.0));

    public LiquefactriumBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(PORT, Direction.NORTH)
            .setValue(PLUMBED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PORT, PLUMBED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiquefactriumBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }


    public static @Nullable Direction portOf(BlockState state) {
        if (!state.is(HexwrightBlocks.LIQUEFACTRIUM_BLOCK) || !state.getValue(PLUMBED)) {
            return null;
        }
        return state.getValue(PORT);
    }

    public static boolean joins(BlockState self, Direction towards) {
        if (!towards.getAxis().isHorizontal()) {
            return false;
        }
        return !self.getValue(PLUMBED) || self.getValue(PORT) == towards;
    }

    private static boolean plumbing(BlockGetter level, BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos.relative(side));
        return state.is(HexwrightBlocks.HEXID_PIPE_BLOCK) || HexidTankColumn.isTank(state);
    }

    private static BlockState claim(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getValue(PLUMBED) && plumbing(level, pos, state.getValue(PORT))) {
            return state;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (plumbing(level, pos, side)) {
                return state.setValue(PORT, side).setValue(PLUMBED, Boolean.TRUE);
            }
        }
        return state.setValue(PLUMBED, Boolean.FALSE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return claim(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (!direction.getAxis().isHorizontal()) {
            return state;
        }
        return claim(level, pos, state);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide || type != HexwrightBlocks.LIQUEFACTRIUM_BLOCK_ENTITY) {
            return null;
        }
        BlockEntityTicker<LiquefactriumBlockEntity> ticker = (l, p, s, be) -> be.serverTick();
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> cast = (BlockEntityTicker<T>) ticker;
        return cast;
    }


    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof BlockItem) {
            return InteractionResult.PASS;
        }
        boolean bottle = held.is(HexwrightItems.HEX_ENGRAVED_BOTTLE);
        if (!held.isEmpty() && !bottle) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof LiquefactriumBlockEntity liquefactrium)) {
            return InteractionResult.PASS;
        }

        if (!liquefactrium.isEmpty()) {
            takeBottle(level, pos, player, liquefactrium);
        } else if (bottle) {
            standBottle(level, pos, player, hand, held, liquefactrium);
        } else {
            report(player, liquefactrium);
        }
        return InteractionResult.CONSUME;
    }

    private static void standBottle(Level level, BlockPos pos, Player player, InteractionHand hand,
                                    ItemStack held, LiquefactriumBlockEntity liquefactrium) {
        liquefactrium.setBottle(held);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            player.setItemInHand(hand, held);
        }
        level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    private static void takeBottle(Level level, BlockPos pos, Player player,
                                   LiquefactriumBlockEntity liquefactrium) {
        ItemStack taken = liquefactrium.takeBottle();
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
        level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.6f, 0.9f);
    }

    private static void report(Player player, LiquefactriumBlockEntity liquefactrium) {
        Direction port = portOf(liquefactrium.getBlockState());
        if (port == null) {
            boolean onAlembix = player.level()
                .getBlockState(liquefactrium.getBlockPos().below())
                .is(HexwrightBlocks.ALEMBIX_BLOCK);
            say(player, onAlembix
                ? "hexwright.liquefactrium.status.on_alembix"
                : "hexwright.liquefactrium.status.unplumbed");
            return;
        }
        Component side = Component.translatable("hexwright.liquefactrium.side."
            + port.getSerializedName());
        if (liquefactrium.portColumns().isEmpty()) {
            say(player, "hexwright.liquefactrium.status.no_tank", side);
            return;
        }
        TankRemnants pool = liquefactrium.portContents();
        if (pool.isEmpty()) {
            say(player, "hexwright.liquefactrium.status.dry", side);
        } else if (pool.kinds() == 1) {
            say(player, "hexwright.liquefactrium.status.holding", side,
                (int) Math.round(pool.total()), pool.largest().label());
        } else {
            say(player, "hexwright.liquefactrium.status.blend", side,
                (int) Math.round(pool.total()), pool.kinds());
        }
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(
            Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof LiquefactriumBlockEntity liquefactrium) {
            ItemStack bottle = liquefactrium.takeBottle();
            if (!bottle.isEmpty()) {
                Block.popResource(level, pos, bottle);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
