package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.server.block.DungeonProp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DungeonProps {

    public enum Room {
        ARENA,
        STAIRWELL,
        JUNCTION,
        PASSAGE,
        DEAD_END
    }

    private static final Map<Room, int[]> COUNTS = new EnumMap<>(Map.of(
        Room.ARENA, new int[]{4, 7},
        Room.STAIRWELL, new int[]{0, 1},
        Room.JUNCTION, new int[]{4, 7},
        Room.PASSAGE, new int[]{2, 5},
        Room.DEAD_END, new int[]{3, 6}));

    private static final Map<Room, List<Weighted>> PALETTES = new EnumMap<>(Room.class);

    private record Weighted(DungeonProp prop, int weight) {
    }

    private static void palette(Room room, Object... pairs) {
        List<Weighted> out = new ArrayList<>(pairs.length / 2);
        for (int index = 0; index < pairs.length; index += 2) {
            out.add(new Weighted((DungeonProp) pairs[index], (Integer) pairs[index + 1]));
        }
        PALETTES.put(room, List.copyOf(out));
    }

    static {
        palette(Room.ARENA,
            DungeonProp.STATUE, 14,
            DungeonProp.ALTAR, 12,
            DungeonProp.SHRINE, 10,
            DungeonProp.THRONE, 10,
            DungeonProp.CANDELABRUM, 14,
            DungeonProp.GILT_CANDLE_STAND, 10,
            DungeonProp.CANDLE_STAND, 8,
            DungeonProp.CANDLE_SKULL, 8,
            DungeonProp.FIRE_CAULDRON, 8,
            DungeonProp.SIGIL_BANNER, 8,
            DungeonProp.HUNG_BANNER, 8,
            DungeonProp.STANDING_BANNER, 6,
            DungeonProp.GIBBET, 7,
            DungeonProp.HANGING_CAGE, 7,
            DungeonProp.HANGED_SKELETON, 6,
            DungeonProp.SARCOPHAGUS, 6,
            DungeonProp.CRYPT, 6,
            DungeonProp.COFFIN, 5,
            DungeonProp.SKULL_PILE, 6,
            DungeonProp.WEAPON_STAND, 6,
            DungeonProp.ARMOURY_RACK, 6);

        palette(Room.STAIRWELL,
            DungeonProp.CANDELABRUM, 25,
            DungeonProp.CANDLE_STAND, 25,
            DungeonProp.CANDLE_SKULL, 20,
            DungeonProp.GILT_CANDLE_STAND, 15,
            DungeonProp.CRATE, 12,
            DungeonProp.TALL_CRATE, 8,
            DungeonProp.OPEN_CRATE, 8,
            DungeonProp.BASKET, 6,
            DungeonProp.SKELETON_REMAINS, 8,
            DungeonProp.SCATTERED_BONES, 6,
            DungeonProp.SHORT_LADDER, 6);

        palette(Room.JUNCTION,
            DungeonProp.LONG_TABLE, 8,
            DungeonProp.ROUND_TABLE, 8,
            DungeonProp.TABLE, 7,
            DungeonProp.DRAPED_TABLE, 6,
            DungeonProp.COVERED_TABLE, 6,
            DungeonProp.SIDE_TABLE, 6,
            DungeonProp.PADDED_CHAIR, 8,
            DungeonProp.WOODEN_CHAIR, 8,
            DungeonProp.CHAIR, 7,
            DungeonProp.STOOL, 8,
            DungeonProp.LOW_STOOL, 6,
            DungeonProp.TALL_STOOL, 6,
            DungeonProp.STONE_BENCH, 6,
            DungeonProp.PLANK_BENCH, 6,
            DungeonProp.BOOKSHELF, 8,
            DungeonProp.WALL_SHELF, 7,
            DungeonProp.STOCKED_SHELF, 7,
            DungeonProp.ALCHEMY_TABLE, 6,
            DungeonProp.SCRIBES_TABLE, 6,
            DungeonProp.NOTICE_BOARD, 6,
            DungeonProp.WEAPON_STAND, 6,
            DungeonProp.SWORD_RACK, 6,
            DungeonProp.EMPTY_RACK, 5,
            DungeonProp.ARMOURY_RACK, 5,
            DungeonProp.ARCHERY_TARGET, 5,
            DungeonProp.TRAINING_DUMMY_1, 3,
            DungeonProp.TRAINING_DUMMY_2, 3,
            DungeonProp.TRAINING_DUMMY_3, 3,
            DungeonProp.TRAINING_DUMMY_4, 3,
            DungeonProp.TRAINING_DUMMY_5, 3,
            DungeonProp.CRATE, 6,
            DungeonProp.TALL_CRATE, 5,
            DungeonProp.STACKED_CRATE, 5,
            DungeonProp.BARREL, 6,
            DungeonProp.CANDELABRUM, 8,
            DungeonProp.CANDLE_STAND, 7,
            DungeonProp.GILT_CANDLE_STAND, 5,
            DungeonProp.FIRE_CAULDRON, 5,
            DungeonProp.HUNG_BANNER, 5,
            DungeonProp.SIGIL_BANNER, 5,
            DungeonProp.STATUE, 4,
            DungeonProp.POTTED_PLANT, 4,
            DungeonProp.VASE, 4,
            DungeonProp.SMALL_VASE, 4,
            DungeonProp.WAGON, 4,
            DungeonProp.MINE_CART, 4,
            DungeonProp.LADEN_MINE_CART, 4,
            DungeonProp.SHRINE, 3,
            DungeonProp.ALTAR, 3);

        palette(Room.PASSAGE,
            DungeonProp.CRATE, 8,
            DungeonProp.TALL_CRATE, 7,
            DungeonProp.LONG_CRATE, 7,
            DungeonProp.STACKED_CRATE, 6,
            DungeonProp.OPEN_CRATE, 6,
            DungeonProp.BARREL, 7,
            DungeonProp.BASKET, 6,
            DungeonProp.APPLE_CRATE, 4,
            DungeonProp.CARROT_CRATE, 4,
            DungeonProp.POTATO_CRATE, 4,
            DungeonProp.APPLE_BASKET, 4,
            DungeonProp.CARROT_BASKET, 4,
            DungeonProp.CANDELABRUM, 9,
            DungeonProp.CANDLE_STAND, 9,
            DungeonProp.CANDLE_SKULL, 8,
            DungeonProp.SKELETON_REMAINS, 6,
            DungeonProp.MOSSY_REMAINS, 6,
            DungeonProp.CHARRED_REMAINS, 5,
            DungeonProp.SPRAWLED_SKELETON, 6,
            DungeonProp.SCATTERED_BONES, 6,
            DungeonProp.SKULL_PILE, 5,
            DungeonProp.HANGED_SKELETON, 5,
            DungeonProp.HANGING_CAGE, 4,
            DungeonProp.FALLEN_SWORD, 4,
            DungeonProp.WAGON, 4,
            DungeonProp.MINE_CART, 5,
            DungeonProp.LADEN_MINE_CART, 5,
            DungeonProp.TABLE, 4,
            DungeonProp.ROUND_TABLE, 4,
            DungeonProp.CHAIR, 4,
            DungeonProp.WOODEN_CHAIR, 4,
            DungeonProp.STOOL, 5,
            DungeonProp.BOOKSHELF, 4,
            DungeonProp.WALL_SHELF, 4,
            DungeonProp.SHORT_LADDER, 5,
            DungeonProp.TALL_LADDER, 4,
            DungeonProp.HUNG_BANNER, 4,
            DungeonProp.NOTICE_BOARD, 3,
            DungeonProp.POTTED_PLANT, 3,
            DungeonProp.VASE, 3);

        palette(Room.DEAD_END,
            DungeonProp.SARCOPHAGUS, 9,
            DungeonProp.COFFIN, 9,
            DungeonProp.CRYPT, 8,
            DungeonProp.SKELETON_REMAINS, 7,
            DungeonProp.MOSSY_REMAINS, 7,
            DungeonProp.CHARRED_REMAINS, 6,
            DungeonProp.SPRAWLED_SKELETON, 7,
            DungeonProp.SCATTERED_BONES, 7,
            DungeonProp.SKULL_PILE, 7,
            DungeonProp.HANGING_CAGE, 6,
            DungeonProp.HANGED_SKELETON, 6,
            DungeonProp.GIBBET, 6,
            DungeonProp.FALLEN_SWORD, 6,
            DungeonProp.CRATE, 8,
            DungeonProp.TALL_CRATE, 7,
            DungeonProp.LONG_CRATE, 6,
            DungeonProp.STACKED_CRATE, 6,
            DungeonProp.OPEN_CRATE, 6,
            DungeonProp.BARREL, 7,
            DungeonProp.BASKET, 6,
            DungeonProp.APPLE_CRATE, 5,
            DungeonProp.CARROT_CRATE, 5,
            DungeonProp.POTATO_CRATE, 5,
            DungeonProp.APPLE_BASKET, 5,
            DungeonProp.CARROT_BASKET, 5,
            DungeonProp.BOOKSHELF, 6,
            DungeonProp.WALL_SHELF, 5,
            DungeonProp.STOCKED_SHELF, 5,
            DungeonProp.CANDELABRUM, 6,
            DungeonProp.CANDLE_STAND, 6,
            DungeonProp.CANDLE_SKULL, 6,
            DungeonProp.ALCHEMY_TABLE, 4,
            DungeonProp.SCRIBES_TABLE, 4,
            DungeonProp.STATUE, 4,
            DungeonProp.ALTAR, 4,
            DungeonProp.SHRINE, 3,
            DungeonProp.THRONE, 3,
            DungeonProp.VASE, 4,
            DungeonProp.SMALL_VASE, 4,
            DungeonProp.POTTED_PLANT, 3,
            DungeonProp.MINE_CART, 4);
    }

    private static final int MOB_CLEARANCE = 3;

    private static final int SPACING = 3;

    private static final int CLUTTER_GAP = 1;

    private static final int CHEST_CLEARANCE = 2;

    private DungeonProps() {
    }

    public record Placed(DungeonProp prop, BlockPos pos, Direction facing) {
        public BlockState state() {
            return this.prop.block().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, this.facing);
        }
    }

    public static Room roomOf(DungeonModules.Module module, boolean arena) {
        if (arena) {
            return Room.ARENA;
        }
        if (module.isStairs()) {
            return Room.STAIRWELL;
        }
        return switch (Integer.bitCount(module.mask())) {
            case 1 -> Room.DEAD_END;
            case 2 -> Room.PASSAGE;
            default -> Room.JUNCTION;
        };
    }

    public static List<Placed> dress(Set<BlockPos> air, Set<BlockPos> rooms, Set<BlockPos> pavement,
                                     Set<BlockPos> clutter, BoundingBox module,
                                     Room room, Set<BlockPos> reserved) {
        RandomSource random = RandomSource.create(
            module.minX() * 341873128712L + module.minZ() * 132897987541L + module.minY() * 7919L);

        List<Weighted> palette = PALETTES.get(room);
        int[] range = COUNTS.get(room);
        int wanted = range[0] + random.nextInt(range[1] - range[0] + 1);

        List<BlockPos> standing = DungeonFittings.fixtureSpots(air, module);
        List<BlockPos> open = new ArrayList<>();
        for (BlockPos floor : DungeonFittings.trapSpots(air, module)) {
            open.add(floor.above());
        }

        List<Placed> placed = new ArrayList<>();
        List<BlockPos> taken = new ArrayList<>();
        for (int attempt = 0; attempt < wanted; attempt++) {
            DungeonProp prop = roll(palette, random);
            int offset = random.nextInt(Math.max(1, standing.size() + open.size()));
            Direction spin = Direction.Plane.HORIZONTAL.getRandomDirection(random);

            List<BlockPos> spots = prop.stance() == DungeonProp.Stance.WALL ? standing : open;
            Placed chosen = pick(spots, offset, air, rooms, pavement, clutter, prop, spin,
                taken, reserved);
            if (chosen == null) {
                continue;
            }
            taken.add(chosen.pos());
            placed.add(chosen);
        }
        return placed;
    }

    private static DungeonProp roll(List<Weighted> palette, RandomSource random) {
        int total = 0;
        for (Weighted entry : palette) {
            total += entry.weight();
        }
        int roll = random.nextInt(total);
        for (Weighted entry : palette) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry.prop();
            }
        }
        return palette.get(palette.size() - 1).prop();
    }

    private static Placed pick(List<BlockPos> spots, int offset, Set<BlockPos> air,
                               Set<BlockPos> rooms, Set<BlockPos> pavement, Set<BlockPos> clutter,
                               DungeonProp prop, Direction spin,
                               List<BlockPos> taken, Set<BlockPos> reserved) {
        if (spots.isEmpty()) {
            return null;
        }
        for (int step = 0; step < spots.size(); step++) {
            BlockPos candidate = spots.get((offset + step) % spots.size());
            Direction facing = prop.stance() == DungeonProp.Stance.WALL
                ? DungeonFittings.openSide(air, candidate)
                : spin;
            if (!fits(candidate, facing, air, rooms, pavement, clutter, prop, taken, reserved)) {
                continue;
            }
            if (prop.stance() != DungeonProp.Stance.CEILING) {
                return new Placed(prop, candidate, facing);
            }
            BlockPos lid = ceiling(air, candidate, prop.shape().hang());
            if (lid != null && !reserved.contains(lid) && clearOf(taken, lid)) {
                return new Placed(prop, lid, facing);
            }
        }
        return null;
    }

    private static boolean clearOf(List<BlockPos> taken, BlockPos pos) {
        for (BlockPos other : taken) {
            if (other.distSqr(pos) < SPACING * SPACING) {
                return false;
            }
        }
        return true;
    }

    private static boolean fits(BlockPos pos, Direction facing, Set<BlockPos> air,
                                Set<BlockPos> rooms, Set<BlockPos> pavement, Set<BlockPos> clutter,
                                DungeonProp prop, List<BlockPos> taken, Set<BlockPos> reserved) {
        if (!rooms.contains(pos) || reserved.contains(pos) || !clearOf(taken, pos)) {
            return false;
        }
        if (prop.stance() != DungeonProp.Stance.CEILING && !pavement.contains(pos.below())) {
            return false;
        }
        if (crowds(clutter, pos)) {
            return false;
        }
        DungeonProp.Shape shape = prop.shape();
        if (prop.stance() != DungeonProp.Stance.CEILING) {
            for (int up = 1; up < shape.headroom(); up++) {
                if (!air.contains(pos.above(up))) {
                    return false;
                }
            }
        }
        Direction side = facing.getClockWise();
        for (int out = 1; out <= shape.sideBlocks(); out++) {
            if (!clear(air, reserved, pos.relative(side, out))
                || !clear(air, reserved, pos.relative(side.getOpposite(), out))) {
                return false;
            }
        }
        for (int out = 1; out <= shape.aheadBlocks(); out++) {
            if (!clear(air, reserved, pos.relative(facing, out))) {
                return false;
            }
        }
        if (prop.stance() != DungeonProp.Stance.WALL) {
            for (int out = 1; out <= shape.behindBlocks(); out++) {
                if (!clear(air, reserved, pos.relative(facing.getOpposite(), out))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean clear(Set<BlockPos> air, Set<BlockPos> reserved, BlockPos pos) {
        return air.contains(pos) && !reserved.contains(pos);
    }

    private static boolean crowds(Set<BlockPos> clutter, BlockPos pos) {
        for (int dx = -CLUTTER_GAP; dx <= CLUTTER_GAP; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -CLUTTER_GAP; dz <= CLUTTER_GAP; dz++) {
                    if (clutter.contains(pos.offset(dx, dy, dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos ceiling(Set<BlockPos> air, BlockPos pos, int drop) {
        BlockPos lid = pos;
        while (air.contains(lid.above())) {
            lid = lid.above();
        }
        if (lid.getY() - pos.getY() < 1) {
            return null;
        }
        for (int down = 1; down <= drop; down++) {
            if (!air.contains(lid.below(down))) {
                return null;
            }
        }
        return lid;
    }

    static void reserveAround(Set<BlockPos> reserved, BlockPos spot) {
        if (spot == null) {
            return;
        }
        for (int dx = -MOB_CLEARANCE; dx <= MOB_CLEARANCE; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -MOB_CLEARANCE; dz <= MOB_CLEARANCE; dz++) {
                    reserved.add(spot.offset(dx, dy, dz));
                }
            }
        }
    }

    static void reserveChest(Set<BlockPos> reserved, BlockPos chest) {
        for (int dx = -CHEST_CLEARANCE; dx <= CHEST_CLEARANCE; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -CHEST_CLEARANCE; dz <= CHEST_CLEARANCE; dz++) {
                    reserved.add(chest.offset(dx, dy, dz));
                }
            }
        }
    }
}
