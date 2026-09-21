package com.bluup.hexwright.server.worldgen.dungeon;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class DungeonPlanner {

    private static final int REACH = 2;

    private static final int GROWTH_ATTEMPTS = 400;

    private static final int HUB_ATTEMPTS = 12;

    public record Cell(int x, int z) {
        Cell step(int direction) {
            return switch (direction) {
                case DungeonModules.NORTH -> new Cell(x, z - 1);
                case DungeonModules.EAST -> new Cell(x + 1, z);
                case DungeonModules.SOUTH -> new Cell(x, z + 1);
                default -> new Cell(x - 1, z);
            };
        }
    }

    public record Placement(DungeonModules.Module module, Rotation rotation, int cellX, int level, int cellZ,
                            Fittings fittings) {
    }

    public record Fittings(boolean crystaliteChest, boolean caveTap, DungeonModules.Fixture fixture,
                           boolean miniboss, DungeonTraps.Spec trap, boolean anchor, boolean servitor,
                           boolean titan) {
        static final Fittings NONE = new Fittings(false, false, null, false, null, false, false, false);
    }

    private static final int MIN_CRYSTALITE_CHESTS = 3;
    private static final int MAX_CRYSTALITE_CHESTS = 4;

    private static final int CAVE_TAPS = 2;

    private static final int[] FIXTURE_WEIGHTS = {55, 35, 10};

    private static final int MIN_TRAPS = 1;

    private static final int[] TITAN_GAPS = {4, 5};

    private record Blob(Set<Cell> cells, Cell hub, List<Cell> frontier, Set<Cell> barred) {
    }

    private DungeonPlanner() {
    }

    public static List<Placement> plan(RandomSource random, int floors, int[] roomsPer, int maxTraps,
                                       Predicate<DungeonModules.Module> titanRoom,
                                       RandomSource extras, float corruptChance) {
        int stairTurns = random.nextInt(4);
        Rotation stairRotation = DungeonModules.rotation(stairTurns);
        Cell stairwell = new Cell(0, 0);

        List<DungeonModules.Module> stairModules = new ArrayList<>(floors);
        List<Blob> storeys = new ArrayList<>(floors);
        for (int level = 0; level < floors; level++) {
            DungeonModules.Module stairs =
                DungeonModules.pick(DungeonModules.stairsFor(level, floors), random);
            stairModules.add(stairs);
            storeys.add(grow(random, stairwell,
                DungeonModules.rotate(stairs.mask(), stairTurns), roomsPer[level]));
        }

        int corruptLevel = -1;
        Cell corruptCell = null;
        if (extras.nextFloat() < corruptChance) {
            corruptLevel = floors > 2 ? 1 + extras.nextInt(floors - 2) : 0;
            Blob storey = storeys.get(corruptLevel);
            corruptCell = findHub(extras, storey.cells(), storey.frontier(), storey.barred(),
                corruptLevel == 0 ? storey.hub() : null);
        }

        List<Placement> placements = new ArrayList<>();
        int hallIndex = -1;
        int corruptIndex = -1;
        for (int level = 0; level < floors; level++) {
            int[] index = layOut(placements, storeys.get(level), stairwell, level,
                stairModules.get(level), stairRotation, level == 0,
                level == corruptLevel ? corruptCell : null, random);
            if (level == 0) {
                hallIndex = index[0];
            }
            if (level == corruptLevel) {
                corruptIndex = index[1];
            }
        }
        return furnish(placements, hallIndex, corruptIndex, floors, maxTraps, titanRoom, random);
    }

    public static int[] roomsPerFloor(int floors, int lowerRooms, int upperRooms) {
        int[] out = new int[floors];
        for (int level = 0; level < floors; level++) {
            out[level] = floors == 1
                ? lowerRooms
                : lowerRooms + Math.round((upperRooms - lowerRooms) * level / (float) (floors - 1));
        }
        return out;
    }

    private static List<Placement> furnish(List<Placement> placements, int hallIndex, int corruptIndex,
                                           int floors, int maxTraps,
                                           Predicate<DungeonModules.Module> titanRoom, RandomSource random) {
        int topLevel = floors - 1;
        Set<Integer> bossRooms = new HashSet<>(List.of(hallIndex, corruptIndex));
        Set<Integer> crystalite = pick(random, placements, MIN_CRYSTALITE_CHESTS
            + random.nextInt(MAX_CRYSTALITE_CHESTS - MIN_CRYSTALITE_CHESTS + 1),
            p -> !p.module().isStairs(), bossRooms);
        Set<Integer> taps = pick(random, placements, CAVE_TAPS, p -> p.level() == topLevel, Set.of(corruptIndex));
        Integer anchorRoom = taps.isEmpty() ? null : taps.iterator().next();

        int wanted = 0;
        int roll = random.nextInt(FIXTURE_WEIGHTS[0] + FIXTURE_WEIGHTS[1] + FIXTURE_WEIGHTS[2]);
        for (int count = 0; count < FIXTURE_WEIGHTS.length; count++) {
            if (roll < FIXTURE_WEIGHTS[count]) {
                wanted = count;
                break;
            }
            roll -= FIXTURE_WEIGHTS[count];
        }
        Set<Integer> fixtures = pick(random, placements, wanted, p -> p.module().isHall(), bossRooms);

        Set<Integer> offLimits = new LinkedHashSet<>(fixtures);
        offLimits.addAll(bossRooms);
        int wantedTraps = maxTraps <= 0 ? 0 : MIN_TRAPS + random.nextInt(maxTraps - MIN_TRAPS + 1);
        Set<Integer> traps = pick(random, placements, wantedTraps,
            p -> !p.module().isStairs(), offLimits);
        Set<Integer> servitors = everyOther(placements, bossRooms);
        Set<Integer> titans = titans(placements, bossRooms, anchorRoom, titanRoom);

        List<Placement> out = new ArrayList<>(placements.size());
        for (int index = 0; index < placements.size(); index++) {
            Placement placement = placements.get(index);
            DungeonModules.Fixture fixture = fixtures.contains(index)
                ? DungeonModules.Fixture.values()[random.nextInt(DungeonModules.Fixture.values().length)]
                : null;
            out.add(new Placement(placement.module(), placement.rotation(), placement.cellX(),
                placement.level(), placement.cellZ(),
                new Fittings(crystalite.contains(index), taps.contains(index), fixture, index == hallIndex,
                    traps.contains(index) ? DungeonTraps.roll(random) : null,
                    anchorRoom != null && anchorRoom == index,
                    servitors.contains(index) && !titans.contains(index), titans.contains(index))));
        }
        return out;
    }

    private static Set<Integer> everyOther(List<Placement> placements, Set<Integer> bossRooms) {
        Set<Integer> chosen = new LinkedHashSet<>();
        int eligible = 0;
        for (int index = 0; index < placements.size(); index++) {
            if (bossRooms.contains(index) || placements.get(index).module().isStairs()) {
                continue;
            }
            if (eligible++ % 2 == 0) {
                chosen.add(index);
            }
        }
        return chosen;
    }

    private static Set<Integer> titans(List<Placement> placements, Set<Integer> bossRooms, Integer anchorRoom,
                                       Predicate<DungeonModules.Module> titanRoom) {
        Set<Integer> chosen = new LinkedHashSet<>();
        int sinceLast = 0;
        for (int index = 0; index < placements.size(); index++) {
            Placement placement = placements.get(index);
            if (bossRooms.contains(index) || placement.module().isStairs()
                || (anchorRoom != null && anchorRoom == index)) {
                continue;
            }
            sinceLast++;
            if (sinceLast >= TITAN_GAPS[chosen.size() % TITAN_GAPS.length] && titanRoom.test(placement.module())) {
                chosen.add(index);
                sinceLast = 0;
            }
        }
        return chosen;
    }

    private static Set<Integer> pick(RandomSource random, List<Placement> placements, int wanted,
                                     Predicate<Placement> eligible, Set<Integer> excluded) {
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < placements.size(); index++) {
            if (!excluded.contains(index) && eligible.test(placements.get(index))) {
                candidates.add(index);
            }
        }
        Set<Integer> chosen = new LinkedHashSet<>();
        while (!candidates.isEmpty() && chosen.size() < wanted) {
            chosen.add(candidates.remove(random.nextInt(candidates.size())));
        }
        return chosen;
    }

    private static Blob grow(RandomSource random, Cell seed, int seedMask, int rooms) {
        Set<Cell> cells = new LinkedHashSet<>();
        Set<Cell> barred = new LinkedHashSet<>();
        List<Cell> frontier = new ArrayList<>();
        cells.add(seed);
        for (int bit = 0; bit < 4; bit++) {
            Cell neighbour = seed.step(1 << bit);
            if ((seedMask & (1 << bit)) != 0) {
                cells.add(neighbour);
                frontier.add(neighbour);
            } else {
                barred.add(neighbour);
            }
        }

        for (int attempt = 0; attempt < GROWTH_ATTEMPTS && cells.size() < rooms; attempt++) {
            Cell from = frontier.get(random.nextInt(frontier.size()));
            Cell to = from.step(1 << random.nextInt(4));
            if (!inReach(to) || barred.contains(to) || cells.contains(to)) {
                continue;
            }
            cells.add(to);
            frontier.add(to);
        }

        return new Blob(cells, findHub(random, cells, frontier, barred, null), frontier, barred);
    }

    private static Cell findHub(RandomSource random, Set<Cell> cells, List<Cell> frontier, Set<Cell> barred,
                                Cell taken) {
        for (int attempt = 0; attempt < HUB_ATTEMPTS; attempt++) {
            Cell hub = tryHub(frontier.get(random.nextInt(frontier.size())), cells, frontier, barred, taken);
            if (hub != null) {
                return hub;
            }
        }
        for (Cell candidate : List.copyOf(frontier)) {
            Cell hub = tryHub(candidate, cells, frontier, barred, taken);
            if (hub != null) {
                return hub;
            }
        }
        return null;
    }

    private static Cell tryHub(Cell candidate, Set<Cell> cells, List<Cell> frontier, Set<Cell> barred,
                               Cell taken) {
        if (candidate.equals(taken)) {
            return null;
        }
        List<Cell> missing = new ArrayList<>();
        for (int bit = 0; bit < 4; bit++) {
            Cell neighbour = candidate.step(1 << bit);
            if (cells.contains(neighbour)) {
                continue;
            }
            if (!inReach(neighbour) || barred.contains(neighbour)) {
                return null;
            }
            missing.add(neighbour);
        }
        cells.addAll(missing);
        frontier.addAll(missing);
        return candidate;
    }

    private static int[] layOut(List<Placement> out, Blob blob, Cell stairwell, int level,
                                DungeonModules.Module stairs, Rotation stairRotation,
                                boolean withHall, Cell corrupt, RandomSource random) {
        int hallIndex = -1;
        int corruptIndex = -1;
        for (Cell cell : blob.cells()) {
            if (cell.equals(stairwell)) {
                out.add(new Placement(stairs, stairRotation, cell.x(), level, cell.z(), Fittings.NONE));
                continue;
            }
            int mask = 0;
            for (int bit = 0; bit < 4; bit++) {
                if (blob.cells().contains(cell.step(1 << bit))) {
                    mask |= 1 << bit;
                }
            }
            boolean isHall = withHall && cell.equals(blob.hub());
            boolean isCorrupt = cell.equals(corrupt);
            DungeonModules.Module module = isHall
                ? DungeonModules.pick(DungeonModules.BOSS_HALLS, random)
                : isCorrupt ? DungeonModules.CORRUPT_HALL
                : DungeonModules.pick(DungeonModules.familyFor(mask), random);
            int turns = DungeonModules.turnsOnto(module.mask(), mask, random);
            if (turns < 0) {
                continue;
            }
            out.add(new Placement(module, DungeonModules.rotation(turns), cell.x(), level, cell.z(),
                Fittings.NONE));
            if (isHall) {
                hallIndex = out.size() - 1;
            }
            if (isCorrupt) {
                corruptIndex = out.size() - 1;
            }
        }
        return new int[] {hallIndex, corruptIndex};
    }

    private static boolean inReach(Cell cell) {
        return Math.abs(cell.x()) <= REACH && Math.abs(cell.z()) <= REACH;
    }
}
