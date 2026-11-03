package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AncientAltarTopBlock extends Block {

    public AncientAltarTopBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        BlockPos altar = pos.below();
        BlockState below = level.getBlockState(altar);
        return below.getBlock() instanceof AncientAltarBlock
            ? below.use(level, player, hand, hit.withPosition(altar))
            : InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockState(pos.below()).getBlock() instanceof AncientAltarBlock) {
            level.removeBlock(pos.below(), false);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        return origin != null
            && params.getLevel().getBlockState(BlockPos.containing(origin).below()).getBlock() instanceof AncientAltarBlock altar
            ? List.of(new ItemStack(altar.variant().portalItem()))
            : List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.below()).getBlock() instanceof AncientAltarBlock altar
            ? new ItemStack(altar.variant().portalItem())
            : ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
