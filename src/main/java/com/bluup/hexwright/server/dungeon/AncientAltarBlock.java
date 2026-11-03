package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AncientAltarBlock extends CrystalAltarBlock {

    private static final VoxelShape SHAPE = box(3.0, 0.0, 3.0, 13.0, 28.0, 13.0);

    private final AncientPortalVariant variant;

    public AncientAltarBlock(AncientPortalVariant variant, Properties properties) {
        super(properties);
        this.variant = variant;
        registerDefaultState(getStateDefinition().any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    public AncientPortalVariant variant() {
        return this.variant;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            AncientPortalBlockEntity portal = portalOf(level, pos);
            if (portal != null) {
                return level.isClientSide
                    ? InteractionResult.SUCCESS
                    : portal.dismantle((ServerLevel) level, (ServerPlayer) player);
            }
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    protected @Nullable TravelStoneBlockEntity stoneFor(ServerLevel level, BlockPos altar, ServerPlayer player) {
        AncientPortalBlockEntity portal = portalOf(level, altar);
        if (portal == null) {
            player.displayClientMessage(Component.translatable("message.hexwright.ancient_portal.gone"), true);
            return null;
        }
        if (!portal.answersTo(player)) {
            player.displayClientMessage(Component.translatable("message.hexwright.ancient_portal.not_owner",
                portal.ownerName()), true);
            return null;
        }
        return portal;
    }

    @Override
    protected Component hint(Level level, BlockPos altar) {
        AncientPortalBlockEntity portal = portalOf(level, altar);
        return portal == null || portal.ownerName().isEmpty()
            ? Component.translatable("message.hexwright.ancient_portal.altar_hint")
            : Component.translatable("message.hexwright.ancient_portal.altar_hint_owned", portal.ownerName());
    }

    private static @Nullable AncientPortalBlockEntity portalOf(Level level, BlockPos altar) {
        if (!(level.getBlockEntity(altar) instanceof AncientAltarBlockEntity entity) || entity.portal() == null) {
            return null;
        }
        return level.getBlockEntity(entity.portal()) instanceof AncientPortalBlockEntity portal ? portal : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AncientAltarBlockEntity entity) {
            BlockPos portal = entity.portal();
            if (portal != null && level.getBlockState(portal).getBlock() instanceof AncientPortalBlock) {
                level.removeBlock(portal, false);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this.variant.portalItem()));
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(this.variant.portalItem());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING,
            rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AncientAltarBlockEntity(pos, state);
    }
}
