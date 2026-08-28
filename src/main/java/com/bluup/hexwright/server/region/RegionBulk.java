package com.bluup.hexwright.server.region;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.mod.HexTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class RegionBulk {

    public static final long FILL_COST_PER_BLOCK = 1250L;

    public static final long BREAK_COST_PER_BLOCK = 1250L;

    public static final long BREAK_COST_CHEAP = 100L;

    public static final int BREAK_EFFECT_SAMPLES = 32;

    private RegionBulk() {
    }

    public record Plan(List<BlockPos> positions, long mediaCost) {
        public boolean isEmpty() {
            return positions.isEmpty();
        }
    }


    @Nullable
    public static ItemStack findFillStack(CastingEnvironment env) {
        return env.queryForMatchingStack(stack -> !stack.isEmpty() && stack.getItem() instanceof BlockItem);
    }

    public static Plan planFill(CastingEnvironment env, Region region, Iota iota, int reverseIdx) {
        ServerLevel level = env.getWorld();
        List<BlockPos> positions = new ArrayList<>();
        RegionBlocks.Cursor cursor = RegionBlocks.cursor(region);
        BlockPos pos;
        int walked = 0;
        while ((pos = cursor.next()) != null) {
            if (++walked > RegionBlocks.MAX_BULK_BLOCKS) {
                throw MishapInvalidIota.of(iota, reverseIdx,
                    "hexwright.region_too_many_blocks", RegionBlocks.MAX_BULK_BLOCKS);
            }
            env.assertPosInRangeForEditing(pos);
            if (level.getBlockState(pos).canBeReplaced()) {
                positions.add(pos);
            }
        }
        return new Plan(positions, (long) positions.size() * FILL_COST_PER_BLOCK);
    }

    public static int affordableCount(CastingEnvironment env, Predicate<ItemStack> matches, int count) {
        if (count <= 0 || env.withdrawItem(matches, count, false)) {
            return count;
        }
        int low = 0;
        int high = count;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (env.withdrawItem(matches, mid, false)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    public static void fill(ServerLevel level, List<BlockPos> positions, BlockState state) {
        for (BlockPos pos : positions) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    public static BlockState fillState(ServerLevel level, BlockPos at, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        BlockState placed = blockItem.getBlock().getStateForPlacement(
            new DirectionalPlaceContext(level, at, Direction.DOWN, stack, Direction.UP));
        return placed != null ? placed : blockItem.getBlock().defaultBlockState();
    }


    public static Plan planBreak(CastingEnvironment env, Region region, Iota iota, int reverseIdx) {
        ServerLevel level = env.getWorld();
        List<BlockPos> positions = new ArrayList<>();
        long cost = 0L;
        RegionBlocks.Cursor cursor = RegionBlocks.cursor(region);
        BlockPos pos;
        int walked = 0;
        while ((pos = cursor.next()) != null) {
            if (++walked > RegionBlocks.MAX_BULK_BLOCKS) {
                throw MishapInvalidIota.of(iota, reverseIdx,
                    "hexwright.region_too_many_blocks", RegionBlocks.MAX_BULK_BLOCKS);
            }
            env.assertPosInRangeForEditing(pos);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0f) {
                continue;
            }
            positions.add(pos);
            cost += state.is(HexTags.Blocks.CHEAP_TO_BREAK_BLOCK) ? BREAK_COST_CHEAP : BREAK_COST_PER_BLOCK;
        }
        return new Plan(positions, cost);
    }

    public static void breakBlocks(ServerLevel level, List<BlockPos> positions, @Nullable LivingEntity breaker) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            Block.dropResources(state, level, pos, blockEntity, breaker, ItemStack.EMPTY);
            FluidState fluid = level.getFluidState(pos);
            level.setBlock(pos, fluid.createLegacyBlock(), Block.UPDATE_CLIENTS);
        }
    }

    public static void playBreakEffects(ServerLevel level, List<BlockPos> positions, List<BlockState> states) {
        int total = positions.size();
        if (total == 0) {
            return;
        }
        int samples = Math.min(BREAK_EFFECT_SAMPLES, total);
        int stride = Math.max(1, total / samples);
        for (int i = 0; i < total; i += stride) {
            level.levelEvent(2001, positions.get(i), Block.getId(states.get(i)));
        }
    }

    public static List<BlockState> captureStates(ServerLevel level, List<BlockPos> positions) {
        List<BlockState> states = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            states.add(level.getBlockState(pos));
        }
        return states;
    }
}
