package com.bluup.hexwright.server.block;

import com.bluup.hexwright.server.item.HexwrightItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class WeaponRackBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final EnumProperty<Column> COLUMN = EnumProperty.create("column", Column.class);

    public static final BooleanProperty UPPER = BooleanProperty.create("upper");

    public static final int SLOTS = 4;

    public static final float ANCHOR_X = 0.621875f;
    public static final float ANCHOR_Z = -0.734375f;

    public static final float TURN = -90f;

    public static final float[] SLOT_X = {-0.9375f, -0.375f, 0.3125f, 0.875f};

    public static final float SLOT_Y = 1.33f;

    public static final float SLOT_Z = 0.09f;

    public static final float MODEL_WIDTH = 3.15625f;
    public static final float MODEL_CENTRE_Y = 1.2546875f;
    public static final float MODEL_CENTRE_Z = -0.0546875f;

    public WeaponRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(FACING, Direction.NORTH)
            .setValue(COLUMN, Column.MIDDLE)
            .setValue(UPPER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, UPPER);
    }

    public static BlockPos middleOf(BlockPos pos, BlockState state) {
        BlockPos level = state.getValue(UPPER) ? pos.below() : pos;
        return level.relative(state.getValue(FACING).getClockWise(), -state.getValue(COLUMN).offset);
    }

    private static BlockPos cell(BlockPos middle, Direction facing, int column, boolean upper) {
        return middle.relative(facing.getClockWise(), column).above(upper ? 1 : 0);
    }

    private BlockState cellState(Direction facing, Column column, boolean upper) {
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(COLUMN, column)
            .setValue(UPPER, upper);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos middle = context.getClickedPos();
        for (Column column : Column.values()) {
            for (int row = 0; row <= 1; row++) {
                BlockPos at = cell(middle, facing, column.offset, row == 1);
                if (at.equals(middle)) {
                    continue;
                }
                if (level.isOutsideBuildHeight(at)
                    || !level.getWorldBorder().isWithinBounds(at)
                    || !level.getBlockState(at).canBeReplaced()
                    || !level.isUnobstructed(null, Shapes.block().move(at.getX(), at.getY(), at.getZ()))) {
                    return null;
                }
            }
        }
        return cellState(facing, Column.MIDDLE, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (Column column : Column.values()) {
            for (int row = 0; row <= 1; row++) {
                BlockPos at = cell(pos, facing, column.offset, row == 1);
                if (!at.equals(pos)) {
                    level.setBlock(at, cellState(facing, column, row == 1), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockPos middle = middleOf(pos, state);
            if (pos.equals(middle)) {
                boolean giveRackBack = true;
                if (level.getBlockEntity(pos) instanceof WeaponRackBlockEntity rack) {
                    rack.dropContents(level, pos);
                    giveRackBack = rack.dropsRack();
                }
                if (giveRackBack) {
                    Block.popResource(level, pos, new ItemStack(this));
                }
                Direction facing = state.getValue(FACING);
                for (Column column : Column.values()) {
                    for (int row = 0; row <= 1; row++) {
                        BlockPos at = cell(pos, facing, column.offset, row == 1);
                        if (!at.equals(pos) && belongsTo(level.getBlockState(at), at, pos)) {
                            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            } else if (belongsTo(level.getBlockState(middle), middle, middle)) {
                level.destroyBlock(middle, false);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()
            && level.getBlockEntity(middleOf(pos, state)) instanceof WeaponRackBlockEntity rack) {
            rack.keepRack();
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    private boolean belongsTo(BlockState state, BlockPos at, BlockPos middle) {
        return state.is(this) && middleOf(at, state).equals(middle);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos middle = middleOf(pos, state);
        if (!(level.getBlockEntity(middle) instanceof WeaponRackBlockEntity rack)) {
            return InteractionResult.PASS;
        }
        int slot = slotAt(middle, state.getValue(FACING), hit.getLocation());
        ItemStack displayed = rack.get(slot);
        ItemStack held = player.getItemInHand(hand);

        if (displayed.isEmpty() && !displayable(held)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!displayed.isEmpty()) {
            rack.set(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(displayed)) {
                player.drop(displayed, false);
            }
            level.playSound(null, middle, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
        } else {
            ItemStack one = held.copy();
            one.setCount(1);
            rack.set(slot, one);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            level.playSound(null, middle, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return InteractionResult.CONSUME;
    }

    public static int slotAt(BlockPos middle, Direction facing, Vec3 hit) {
        Direction along = facing.getClockWise();
        double offset = (hit.x - (middle.getX() + 0.5)) * along.getStepX()
            + (hit.z - (middle.getZ() + 0.5)) * along.getStepZ();
        int nearest = 0;
        for (int slot = 1; slot < SLOTS; slot++) {
            if (Math.abs(offset - SLOT_X[slot]) < Math.abs(offset - SLOT_X[nearest])) {
                nearest = slot;
            }
        }
        return nearest;
    }

    public static boolean displayable(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof TieredItem
            || item instanceof ProjectileWeaponItem
            || item instanceof TridentItem
            || stack.is(HexwrightItemTags.WEAPON_RACK);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state
            .setValue(FACING, mirror.mirror(state.getValue(FACING)))
            .setValue(COLUMN, state.getValue(COLUMN).opposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(COLUMN) == Column.MIDDLE && !state.getValue(UPPER)
            ? new WeaponRackBlockEntity(pos, state)
            : null;
    }

    public enum Column implements StringRepresentable {
        LEFT(-1),
        MIDDLE(0),
        RIGHT(1);

        private final int offset;

        Column(int offset) {
            this.offset = offset;
        }

        public Column opposite() {
            return switch (this) {
                case LEFT -> RIGHT;
                case MIDDLE -> MIDDLE;
                case RIGHT -> LEFT;
            };
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
