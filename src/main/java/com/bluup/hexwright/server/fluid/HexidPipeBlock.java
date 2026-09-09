package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HexidPipeBlock extends Block {

    public static final EnumProperty<PipeJoint> NORTH = EnumProperty.create("north", PipeJoint.class);
    public static final EnumProperty<PipeJoint> EAST = EnumProperty.create("east", PipeJoint.class);
    public static final EnumProperty<PipeJoint> SOUTH = EnumProperty.create("south", PipeJoint.class);
    public static final EnumProperty<PipeJoint> WEST = EnumProperty.create("west", PipeJoint.class);
    public static final EnumProperty<PipeJoint> UP = EnumProperty.create("up", PipeJoint.class);
    public static final EnumProperty<PipeJoint> DOWN = EnumProperty.create("down", PipeJoint.class);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final EnumProperty<PipeRun> RUN = EnumProperty.create("run", PipeRun.class);

    public static final Map<Direction, EnumProperty<PipeJoint>> JOINTS = new EnumMap<>(Direction.class);

    static {
        JOINTS.put(Direction.NORTH, NORTH);
        JOINTS.put(Direction.EAST, EAST);
        JOINTS.put(Direction.SOUTH, SOUTH);
        JOINTS.put(Direction.WEST, WEST);
        JOINTS.put(Direction.UP, UP);
        JOINTS.put(Direction.DOWN, DOWN);
    }

    private static final VoxelShape HUB = box(7, 0, 7, 9, 2, 9);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final VoxelShape RISER = Shapes.or(
        box(7, 2, 7, 9, 16, 9), box(6.75, 8, 6.75, 9.25, 10, 9.25));
    private static final VoxelShape LID_COLLAR = box(6.75, 0, 6.75, 9.25, 2.25, 9.25);
    private static final VoxelShape LID_BASE = box(6.75, 0, 6.75, 9.25, 2.5, 9.25);

    static {
        ARMS.put(Direction.NORTH, Shapes.or(box(7, 0, 1, 9, 2, 7), box(6.75, 0, 0, 9.25, 2.25, 1)));
        ARMS.put(Direction.EAST, Shapes.or(box(9, 0, 7, 15, 2, 9), box(15, 0, 6.75, 16, 2.25, 9.25)));
        ARMS.put(Direction.SOUTH, Shapes.or(box(7, 0, 9, 9, 2, 15), box(6.75, 0, 15, 9.25, 2.25, 16)));
        ARMS.put(Direction.WEST, Shapes.or(box(1, 0, 7, 7, 2, 9), box(0, 0, 6.75, 1, 2.25, 9.25)));
    }

    private final Map<BlockState, VoxelShape> shapes;

    public HexidPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, PipeJoint.NONE)
            .setValue(EAST, PipeJoint.NONE)
            .setValue(SOUTH, PipeJoint.NONE)
            .setValue(WEST, PipeJoint.NONE)
            .setValue(UP, PipeJoint.NONE)
            .setValue(DOWN, PipeJoint.NONE)
            .setValue(FACING, Direction.NORTH)
            .setValue(RUN, PipeRun.FREE));
        this.shapes = buildShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, FACING, RUN);
    }


    private Map<BlockState, VoxelShape> buildShapes() {
        Map<Integer, VoxelShape> distinct = new HashMap<>();
        Map<BlockState, VoxelShape> built = new HashMap<>();
        for (BlockState state : getStateDefinition().getPossibleStates()) {
            built.put(state, distinct.computeIfAbsent(shapeKey(state), key -> assemble(state)));
        }
        return built;
    }

    private static int shapeKey(BlockState state) {
        int key = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            key = key * 2 + (state.getValue(JOINTS.get(side)).joined() ? 1 : 0);
        }
        key = key * 2 + (state.getValue(UP).joined() ? 1 : 0);
        key = key * 2 + (state.getValue(UP) == PipeJoint.PIPE ? 1 : 0);
        key = key * 3 + state.getValue(DOWN).ordinal();
        return key * 4 + state.getValue(FACING).get2DDataValue();
    }

    private static VoxelShape assemble(BlockState state) {
        List<VoxelShape> pieces = new ArrayList<>();
        pieces.add(HUB);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (state.getValue(JOINTS.get(side)).joined()) {
                pieces.add(ARMS.get(side));
            }
        }
        if (state.getValue(UP).joined()) {
            pieces.add(RISER);
        }
        if (state.getValue(DOWN) == PipeJoint.TANK) {
            if (state.getValue(UP) == PipeJoint.PIPE) {
                pieces.add(LID_BASE);
            } else if (carriesSomewhere(state)) {
                pieces.add(LID_COLLAR);
            }
        }
        if (isolated(state)) {
            pieces.add(ARMS.get(state.getValue(FACING)));
        }
        return Shapes.or(Shapes.empty(), pieces.toArray(new VoxelShape[0])).optimize();
    }

    private static boolean isolated(BlockState state) {
        for (Direction side : Direction.values()) {
            if (state.getValue(JOINTS.get(side)).joined()) {
                return false;
            }
        }
        return true;
    }

    private static boolean carriesSomewhere(BlockState state) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (state.getValue(JOINTS.get(side)).joined()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.getOrDefault(state, HUB);
    }


    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        PipeRun run = player != null && player.isShiftKeyDown()
            ? PipeRun.along(context.getNearestLookingDirection().getAxis())
            : PipeRun.FREE;
        BlockState state = defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection())
            .setValue(RUN, run);
        for (Direction side : Direction.values()) {
            BlockPos neighbour = context.getClickedPos().relative(side);
            state = state.setValue(JOINTS.get(side),
                jointWith(state, context.getLevel().getBlockState(neighbour), side));
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        String clash = HexidPipeNetwork.clashReason(level, pos);
        if (clash != null) {
            breakClash(level, pos, placer, clash);
            return;
        }
        if (state.getValue(RUN).isStraight() && placer instanceof Player player) {
            say(player, "hexwright.hexid_pipe.straight");
        }
    }

    private static void breakClash(Level level, BlockPos pos, @Nullable LivingEntity placer,
                                   String reason) {
        level.destroyBlock(pos, true);
        if (placer instanceof Player player) {
            say(player, reason);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        PipeJoint joint = jointWith(state, neighbour, direction);
        if (joint == state.getValue(JOINTS.get(direction))) {
            return state;
        }
        level.scheduleTick(pos, this, 1);
        return state.setValue(JOINTS.get(direction), joint);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        String clash = HexidPipeNetwork.settle(level, pos);
        if (clash != null) {
            breakClash(level, pos, null, clash);
        }
    }

    public static PipeJoint jointWith(BlockState self, BlockState neighbour, Direction towards) {
        if (!runOf(self).allows(towards)) {
            return PipeJoint.NONE;
        }
        if (neighbour.is(HexwrightBlocks.HEXID_PIPE_BLOCK)) {
            return runOf(neighbour).allows(towards) ? PipeJoint.PIPE : PipeJoint.NONE;
        }
        if (neighbour.is(HexwrightBlocks.ALEMBIX_BLOCK)) {
            return towards == Direction.UP ? PipeJoint.NONE : PipeJoint.TANK;
        }
        if (!HexidTankColumn.isTank(neighbour)) {
            return PipeJoint.NONE;
        }
        TankPart part = neighbour.getValue(HexidTankBlock.PART);
        boolean joins = towards == Direction.DOWN ? part.lidded() : part.footed();
        return joins ? PipeJoint.TANK : PipeJoint.NONE;
    }

    private static PipeRun runOf(BlockState state) {
        return state.hasProperty(RUN) ? state.getValue(RUN) : PipeRun.FREE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!state.is(old.getBlock())) {
            HexidPipeNetwork.wake(level, pos);
        }
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
        report(level, pos, player);
        return InteractionResult.CONSUME;
    }

    private static void report(Level level, BlockPos pos, Player player) {
        List<HexidTankBlockEntity> tanks = HexidPipeNetwork.tanksOn(level, pos);
        if (tanks.isEmpty()) {
            say(player, "hexwright.hexid_pipe.status.dry");
            return;
        }
        if (HexidPipeNetwork.isSuspension(tanks)) {
            reportSuspension(player, tanks);
            return;
        }
        long amount = 0;
        long capacity = 0;
        long media = 0;
        for (HexidTankBlockEntity tank : tanks) {
            amount += tank.amountMb();
            capacity += tank.capacityMb();
            media += tank.totalMedia();
        }
        long density = amount > 0 ? media / amount : 0;
        if (amount <= 0) {
            say(player, "hexwright.hexid_pipe.status.empty", tanks.size(), count(capacity));
        } else if (media <= 0) {
            say(player, "hexwright.hexid_pipe.status.water", tanks.size(), count(amount), count(capacity));
        } else {
            say(player, "hexwright.hexid_pipe.status.hexid", tanks.size(), count(amount), count(capacity),
                String.format("%.2f", density / 100.0), count(media));
        }
    }

    private static void reportSuspension(Player player, List<HexidTankBlockEntity> tanks) {
        double held = 0;
        double capacity = 0;
        int columns = 0;
        for (HexidTankBlockEntity tank : tanks) {
            if (tank.holdsFluid()) {
                continue;
            }
            held += tank.remnants().total();
            capacity += tank.remnantCapacity();
            columns++;
        }
        say(player, "hexwright.hexid_pipe.status.suspension",
            columns, count(Math.round(held)), count(Math.round(capacity)));
    }

    private static String count(long value) {
        return String.format("%,d", value);
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(
            Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }


    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
            .setValue(RUN, state.getValue(RUN).rotate(rotation));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(JOINTS.get(rotation.rotate(side)), state.getValue(JOINTS.get(side)));
        }
        return turned;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            mirrored = mirrored.setValue(JOINTS.get(mirror.mirror(side)), state.getValue(JOINTS.get(side)));
        }
        return mirrored;
    }
}
