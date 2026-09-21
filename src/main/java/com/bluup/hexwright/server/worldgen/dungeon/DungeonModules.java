package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class DungeonModules {

    public static final int NORTH = 1;
    public static final int EAST = 1 << 1;
    public static final int SOUTH = 1 << 2;
    public static final int WEST = 1 << 3;
    public static final int ALL = NORTH | EAST | SOUTH | WEST;

    public record Module(String name, int mask, boolean stairs, boolean hall) {
        public ResourceLocation template() {
            return Hexwright.id("dungeon/" + name);
        }

        public boolean isStairs() {
            return this.stairs;
        }

        public boolean isHall() {
            return this.hall;
        }
    }

    public enum Fixture {
        CRUCIBLE(() -> HexwrightBlocks.CRUCIBLE_BLOCK),
        ESSENCE_FORGE(() -> HexwrightBlocks.WORKTABLE_BLOCK),
        STAFF_ASSEMBLY(() -> HexwrightBlocks.STAFF_ASSEMBLY_BLOCK);

        private final Supplier<Block> block;

        Fixture(Supplier<Block> block) {
            this.block = block;
        }

        public Block block() {
            return this.block.get();
        }
    }

    private static Module room(String name, int mask) {
        return new Module(name, mask, false, false);
    }

    private static Module hall(String name, int mask) {
        return new Module(name, mask, false, true);
    }

    private static Module stairs(String name, int mask) {
        return new Module(name, mask, true, false);
    }

    public static final List<Module> DEAD_END = List.of(
        room("deadend_1", WEST),
        room("deadend_2", WEST),
        room("premium_deadend_1", WEST),
        room("premium_deadend_2", WEST),
        room("premium_deadend_3", WEST),
        room("premium_deadend_4", WEST),
        room("premium_deadend_5", WEST));

    public static final List<Module> STRAIGHT = List.of(
        room("straight_1", EAST | WEST),
        room("straight_2", EAST | WEST),
        room("premium_straight_1", EAST | WEST),
        room("premium_straight_2", EAST | WEST),
        room("premium_straight_3", EAST | WEST),
        room("premium_straight_4", EAST | WEST),
        room("premium_straight_5", EAST | WEST));

    public static final List<Module> CORNER = List.of(
        room("corner_1", NORTH | WEST),
        room("corner_2", NORTH | WEST),
        room("premium_corner_1", NORTH | WEST),
        room("premium_corner_2", NORTH | WEST),
        room("premium_corner_3", NORTH | WEST),
        room("premium_corner_4", NORTH | WEST),
        room("premium_corner_5", NORTH | WEST));

    public static final List<Module> THREEWAY = List.of(
        hall("threeway_1", SOUTH | EAST | WEST),
        hall("threeway_2", SOUTH | EAST | WEST),
        hall("premium_threeway_1", NORTH | EAST | WEST),
        hall("premium_threeway_2", NORTH | EAST | WEST),
        hall("premium_threeway_3", NORTH | EAST | WEST),
        hall("premium_threeway_4", NORTH | EAST | WEST),
        hall("premium_threeway_5", NORTH | EAST | WEST));

    public static final List<Module> FOURWAY = List.of(
        hall("fourway_1", ALL),
        hall("fourway_2", ALL),
        hall("center_2", ALL),
        hall("premium_center_3", ALL),
        hall("premium_fourway_1", ALL),
        hall("premium_fourway_2", ALL),
        hall("premium_fourway_3", ALL),
        hall("premium_fourway_4", ALL),
        hall("premium_fourway_5", ALL),
        hall("premium_fourway_6", ALL));

    public static final List<Module> BOSS_HALLS = List.of(
        hall("center_1", ALL),
        hall("premium_center_1", ALL),
        hall("premium_center_2", ALL),
        hall("premium_center_3", ALL));

    public static final Module CORRUPT_HALL = hall("corrupt_boss_room", ALL);

    public static final List<Module> BOTTOM_STAIRS = List.of(
        stairs("stairsdown", EAST | WEST),
        stairs("premium_down_deadend", WEST),
        stairs("premium_down_straight", EAST | WEST),
        stairs("premium_down_threeway", NORTH | SOUTH | WEST));

    public static final List<Module> MIDDLE_STAIRS = List.of(
        stairs("stairsmiddle", NORTH | SOUTH | WEST),
        stairs("premium_middle_straight", EAST | WEST),
        stairs("premium_middle_threeway", NORTH | SOUTH | WEST),
        stairs("premium_middle_fourway", ALL));

    public static final List<Module> TOP_STAIRS = List.of(
        stairs("stairsup", NORTH | SOUTH | WEST),
        stairs("premium_up_corner", NORTH | WEST),
        stairs("premium_up_threeway", NORTH | EAST | WEST),
        stairs("premium_up_fourway", ALL));

    private DungeonModules() {
    }

    public static int rotate(int mask, int quarterTurns) {
        int out = 0;
        for (int bit = 0; bit < 4; bit++) {
            if ((mask & (1 << bit)) != 0) {
                out |= 1 << ((bit + quarterTurns) & 3);
            }
        }
        return out;
    }

    public static int turnsOnto(int from, int onto, RandomSource random) {
        int matches = 0;
        int chosen = -1;
        for (int turns = 0; turns < 4; turns++) {
            if (rotate(from, turns) == onto && random.nextInt(++matches) == 0) {
                chosen = turns;
            }
        }
        return chosen;
    }

    public static Rotation rotation(int quarterTurns) {
        return Rotation.values()[quarterTurns & 3];
    }

    public static List<Module> familyFor(int mask) {
        return switch (Integer.bitCount(mask)) {
            case 1 -> DEAD_END;
            case 2 -> (mask == (NORTH | SOUTH) || mask == (EAST | WEST)) ? STRAIGHT : CORNER;
            case 3 -> THREEWAY;
            default -> FOURWAY;
        };
    }

    public static Module pick(List<Module> family, RandomSource random) {
        return family.get(random.nextInt(family.size()));
    }

    public static List<Module> stairsFor(int level, int floors) {
        if (level == 0) {
            return BOTTOM_STAIRS;
        }
        return level == floors - 1 ? TOP_STAIRS : MIDDLE_STAIRS;
    }

    public static Module byTemplate(String template) {
        Map<String, Module> index = TEMPLATES;
        if (index == null) {
            index = new HashMap<>();
            for (Module module : all()) {
                index.put(module.template().toString(), module);
            }
            TEMPLATES = index;
        }
        return index.get(template);
    }

    private static volatile Map<String, Module> TEMPLATES;

    public static List<Module> all() {
        List<Module> out = new ArrayList<>();
        out.addAll(DEAD_END);
        out.addAll(STRAIGHT);
        out.addAll(CORNER);
        out.addAll(THREEWAY);
        out.addAll(FOURWAY);
        out.addAll(BOSS_HALLS);
        out.add(CORRUPT_HALL);
        out.addAll(BOTTOM_STAIRS);
        out.addAll(MIDDLE_STAIRS);
        out.addAll(TOP_STAIRS);
        return out;
    }
}
