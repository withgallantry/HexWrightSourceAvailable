package com.bluup.hexwright.server.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AncientPortalBlock extends TravelStoneBlock {

    private static final int ALTAR_FRONT = 3;
    private static final int ALTAR_SIDE = 3;

    private final AncientPortalVariant variant;

    public AncientPortalBlock(AncientPortalVariant variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    public AncientPortalVariant variant() {
        return this.variant;
    }

    @Override
    public TravelStoneCollision collision() {
        return TravelStoneCollision.ANCIENT_PORTAL;
    }

    static BlockPos altarPos(BlockPos portal, Direction front) {
        return portal.relative(front, ALTAR_FRONT).relative(front.getClockWise(), ALTAR_SIDE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        BlockPos altar = altarPos(context.getClickedPos(), state.getValue(HorizontalDirectionalBlock.FACING));
        Level level = context.getLevel();
        return level.getWorldBorder().isWithinBounds(altar) && level.getBlockState(altar).canBeReplaced(context)
            ? state
            : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof AncientPortalBlockEntity portal) {
            portal.setOwner(player);
        }
        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos altar = altarPos(pos, front);
        level.setBlock(altar, this.variant.altar().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, front),
            Block.UPDATE_ALL);
        if (level.getBlockEntity(altar) instanceof AncientAltarBlockEntity entity) {
            entity.bindPortal(pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockPos altar = altarPos(pos, state.getValue(HorizontalDirectionalBlock.FACING));
            if (level.getBlockState(altar).getBlock() instanceof AncientAltarBlock
                && level.getBlockEntity(altar) instanceof AncientAltarBlockEntity entity && pos.equals(entity.portal())) {
                level.removeBlock(altar, false);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
            || !(level.getBlockEntity(pos) instanceof AncientPortalBlockEntity portal)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return portal.dismantle((ServerLevel) level, (ServerPlayer) player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this.variant.portalItem()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hexwright.ancient_portal").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.ancient_portal.altar").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.ancient_portal.dismantle")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AncientPortalBlockEntity(pos, state);
    }
}
