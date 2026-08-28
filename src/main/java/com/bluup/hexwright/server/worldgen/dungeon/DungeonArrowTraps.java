package com.bluup.hexwright.server.worldgen.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DungeonArrowTraps {

    private static final int MIN_LOADED_SLOTS = 5;
    private static final int MAX_LOADED_SLOTS = 9;
    private static final int MIN_ARROWS = 4;
    private static final int MAX_ARROWS = 12;

    private static final Potion[] TIPS = {
        null, null, null, null, null, null, null,
        Potions.POISON, Potions.POISON, Potions.STRONG_POISON,
        Potions.SLOWNESS, Potions.SLOWNESS, Potions.STRONG_SLOWNESS,
        Potions.WEAKNESS,
        Potions.HARMING, Potions.STRONG_HARMING,
    };

    private static final int SPECTRAL_IN = 8;

    private DungeonArrowTraps() {
    }

    static void arm(ServerLevelAccessor level, BoundingBox box, Set<BlockPos> air,
                    List<BlockPos> dispensers, List<BlockPos> wire) {
        for (BlockPos pos : dispensers) {
            if (box.isInside(pos)) {
                stock(level, pos);
            }
        }
        for (List<BlockPos> ladder : ladders(air, wire)) {
            lay(level, box, ladder);
        }
    }

    private static void stock(ServerLevelAccessor level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof DispenserBlockEntity dispenser)) {
            return;
        }
        RandomSource random = RandomSource.create(Mth.getSeed(pos.getX(), pos.getY(), pos.getZ()));
        int loaded = MIN_LOADED_SLOTS + random.nextInt(MAX_LOADED_SLOTS - MIN_LOADED_SLOTS + 1);
        for (int slot = 0; slot < loaded; slot++) {
            dispenser.setItem(slot, arrows(random));
        }
    }

    private static ItemStack arrows(RandomSource random) {
        int count = MIN_ARROWS + random.nextInt(MAX_ARROWS - MIN_ARROWS + 1);
        if (random.nextInt(SPECTRAL_IN) == 0) {
            return new ItemStack(Items.SPECTRAL_ARROW, count);
        }
        Potion tip = TIPS[random.nextInt(TIPS.length)];
        if (tip == null) {
            return new ItemStack(Items.ARROW, count);
        }
        return PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW, count), tip);
    }

    private static List<List<BlockPos>> ladders(Set<BlockPos> air, List<BlockPos> wire) {
        Map<Long, List<BlockPos>> columns = new HashMap<>();
        for (BlockPos pos : wire) {
            columns.computeIfAbsent(BlockPos.asLong(pos.getX(), 0, pos.getZ()),
                key -> new ArrayList<>()).add(pos);
        }
        List<List<BlockPos>> found = new ArrayList<>();
        for (List<BlockPos> column : columns.values()) {
            if (column.size() < 2) {
                continue;
            }
            column.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
            List<BlockPos> ladder = climb(air, column);
            if (ladder != null) {
                found.add(ladder);
            }
        }
        return found;
    }

    private static List<BlockPos> climb(Set<BlockPos> air, List<BlockPos> column) {
        for (int i = 1; i < column.size(); i++) {
            if (column.get(i).getY() - column.get(i - 1).getY() != 2) {
                return null;
            }
        }
        BlockPos lowest = column.get(0);
        Direction side = null;
        for (Direction candidate : Direction.Plane.HORIZONTAL) {
            if (footed(air, lowest.relative(candidate).above())) {
                side = candidate;
                break;
            }
        }
        if (side == null) {
            return null;
        }
        List<BlockPos> ladder = new ArrayList<>(column.size() * 2 + 1);
        BlockPos feed = lowest.relative(side).below();
        if (footed(air, feed)) {
            ladder.add(feed);
        }
        for (BlockPos step : column) {
            ladder.add(step);
            BlockPos rung = step.relative(side).above();
            if (footed(air, rung)) {
                ladder.add(rung);
            }
        }
        return ladder;
    }

    private static boolean footed(Set<BlockPos> air, BlockPos pos) {
        return air.contains(pos) && !air.contains(pos.below());
    }

    private static void lay(ServerLevelAccessor level, BoundingBox box, List<BlockPos> ladder) {
        for (int i = 0; i < ladder.size(); i++) {
            BlockPos pos = ladder.get(i);
            if (!box.isInside(pos)) {
                continue;
            }
            BlockPos above = i + 1 < ladder.size() ? ladder.get(i + 1) : null;
            BlockPos below = i > 0 ? ladder.get(i - 1) : null;
            level.setBlock(pos, wire(pos, above, below), Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState wire(BlockPos pos, BlockPos above, BlockPos below) {
        BlockState state = Blocks.REDSTONE_WIRE.defaultBlockState();
        BlockPos rung = above != null ? above : below;
        if (rung == null) {
            return state;
        }
        Direction step = towards(pos, rung);
        return state
            .setValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(step),
                above != null ? RedstoneSide.UP : RedstoneSide.SIDE)
            .setValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(step.getOpposite()),
                RedstoneSide.SIDE);
    }

    private static Direction towards(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        if (dx != 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return to.getZ() - from.getZ() > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
