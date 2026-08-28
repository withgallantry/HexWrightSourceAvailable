package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;

import java.util.List;
import java.util.function.Supplier;

public final class DungeonModules {

    public static final int NORTH = 1;
    public static final int EAST = 1 << 1;
    public static final int SOUTH = 1 << 2;
    public static final int WEST = 1 << 3;
    public static final int ALL = NORTH | EAST | SOUTH | WEST;

    public record Module(String name, int mask) {
        public ResourceLocation template() {
            return Hexwright.id("dungeon/" + name);
        }

        public boolean isStairs() {
            return name.startsWith("stairs");
        }

        public boolean isHall() {
            return name.startsWith("center") || name.startsWith("fourway") || name.startsWith("threeway");
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

    public static final List<Module> DEAD_END = List.of(
        new Module("deadend_1", WEST),
        new Module("deadend_2", WEST));

    public static final List<Module> STRAIGHT = List.of(
        new Module("straight_1", EAST | WEST),
        new Module("straight_2", EAST | WEST));

    public static final List<Module> CORNER = List.of(
        new Module("corner_1", NORTH | WEST),
        new Module("corner_2", NORTH | WEST));

    public static final List<Module> THREEWAY = List.of(
        new Module("threeway_1", SOUTH | EAST | WEST),
        new Module("threeway_2", SOUTH | EAST | WEST));

    public static final List<Module> FOURWAY = List.of(
        new Module("fourway_1", ALL),
        new Module("fourway_2", ALL),
        new Module("center_2", ALL));

    public static final Module BOSS_HALL = new Module("center_1", ALL);

    public static final Module STAIRS_DOWN = new Module("stairsdown", EAST | WEST);
    public static final Module STAIRS_UP = new Module("stairsup", NORTH | SOUTH | WEST);

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
}
