package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record DungeonSafeRoom(BlockPos shaft, boolean door) {

    private static final int INTERIOR = 15;
    private static final int INTERIOR_HEIGHT = 8;

    private static final int CENTRE = INTERIOR / 2;

    private static final int[] BEAMS = {CENTRE - 4, CENTRE + 4};

    private static final int[] BAY_MIDDLES = {1, CENTRE, INTERIOR - 2};

    private static final int DAIS_RADIUS = 3;
    private static final int DAIS_HEIGHT = 1;

    private static final int SHAFT_I = BAY_MIDDLES[2];
    private static final int SHAFT_K = BAY_MIDDLES[2];

    private static final int ARRIVAL_K = INTERIOR - 2;

    private static final int[] PILLAR_I = {CENTRE - DAIS_RADIUS, CENTRE + DAIS_RADIUS};

    private static final int ALTAR_I = PILLAR_I[0];
    private static final int ALTAR_K = CENTRE + 1;

    private static final int DAIS_LIGHT_OFFSET = 2;

    private static final int BENCH_HALF_LENGTH = 2;

    private static final int SLIT_FROM = 3;
    private static final int SLIT_TO = 5;

    private static final int DOOR_K = CENTRE;
    private static final int DOOR_HEIGHT = 3;

    private static final Block SEAL = Blocks.PURPLE_STAINED_GLASS;

    public static final float ARRIVAL_YAW = 180.0F;

    public DungeonSafeRoom(BlockPos shaft) {
        this(shaft, false);
    }

    public static DungeonSafeRoom openingEastOnto(BlockPos threshold) {
        return new DungeonSafeRoom(new BlockPos(threshold.getX() - 1 - INTERIOR + SHAFT_I, threshold.getY() - 1,
            threshold.getZ() - DOOR_K + SHAFT_K), true);
    }

    private int minX() {
        return this.shaft.getX() - SHAFT_I;
    }

    private int minZ() {
        return this.shaft.getZ() - SHAFT_K;
    }

    private int floorY() {
        return this.shaft.getY() + 1;
    }

    private int daisTopY() {
        return floorY() + 1 + DAIS_HEIGHT;
    }

    private boolean hasLadder() {
        return !this.door;
    }

    public BoundingBox box() {
        return new BoundingBox(minX() - 1, floorY(), minZ() - 1,
            minX() + INTERIOR, floorY() + INTERIOR_HEIGHT + 1, minZ() + INTERIOR);
    }

    public AABB interior() {
        return new AABB(minX(), floorY() + 1, minZ(),
            minX() + INTERIOR, floorY() + 1 + INTERIOR_HEIGHT, minZ() + INTERIOR);
    }

    public Vec3 arrival() {
        return new Vec3(minX() + CENTRE + 0.5, floorY() + 1, minZ() + ARRIVAL_K + 0.5);
    }

    public PortalWindow window(double sill) {
        double diameter = TravelStones.WINDOW_DIAMETER;
        Vec3 origin = new Vec3(minX() + CENTRE + 0.5 - diameter / 2.0, daisTopY() + sill,
            minZ() + CENTRE + 0.5);
        return new PortalWindow(origin, new Vec3(diameter, 0.0, 0.0), new Vec3(0.0, diameter, 0.0), true);
    }

    public BlockPos altar() {
        return new BlockPos(minX() + ALTAR_I, daisTopY(), minZ() + ALTAR_K);
    }

    public DungeonRift exit(int instance, long seed) {
        Vec3 center = new Vec3(minX() + CENTRE + 0.5, daisTopY() + DungeonRift.HALF_LENGTH, minZ() + CENTRE + 0.5);
        return new DungeonRift(instance, DungeonRift.Kind.EXIT, center, Direction.NORTH, seed, 0L, 0L);
    }

    public boolean seal(ServerLevel level, boolean shut) {
        boolean changed = false;
        for (BlockPos pos : sealPositions()) {
            boolean there = level.getBlockState(pos).is(SEAL);
            if (shut && !there) {
                level.setBlock(pos, SEAL.defaultBlockState(), Block.UPDATE_ALL);
                changed = true;
            } else if (!shut && there) {
                BlockState opened = hasLadder() ? level.getBlockState(pos.below()) : Blocks.AIR.defaultBlockState();
                level.setBlock(pos, opened, Block.UPDATE_ALL);
                changed = true;
            }
        }
        return changed;
    }

    public BlockPos sealAnchor() {
        return sealPositions().get(0);
    }

    private List<BlockPos> sealPositions() {
        if (hasLadder()) {
            return List.of(new BlockPos(this.shaft.getX(), floorY(), this.shaft.getZ()));
        }
        List<BlockPos> doorway = new ArrayList<>();
        for (int dy = 1; dy <= DOOR_HEIGHT; dy++) {
            for (int k = DOOR_K - 1; k <= DOOR_K + 1; k++) {
                doorway.add(new BlockPos(minX() + INTERIOR, floorY() + dy, minZ() + k));
            }
        }
        return doorway;
    }

    public void build(ServerLevel level) {
        BoundingBox box = box();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    level.setBlock(pos.set(x, y, z), blockAt(x - minX(), y - floorY(), z - minZ()), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private BlockState blockAt(int i, int dy, int k) {
        if (i < 0 || k < 0 || i >= INTERIOR || k >= INTERIOR) {
            return wallAt(i, dy, k);
        }
        if (dy == 0) {
            return floorAt(i, k);
        }
        if (dy == INTERIOR_HEIGHT + 1) {
            return ceilingAt(i, k);
        }
        return fittingAt(i, dy, k);
    }

    private BlockState wallAt(int i, int dy, int k) {
        if (dy == 0 || dy == INTERIOR_HEIGHT + 1) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        boolean doorWall = this.door && i == INTERIOR;
        if (doorWall) {
            BlockState doorway = doorwayAt(dy, k);
            if (doorway != null) {
                return doorway;
            }
        }
        boolean acrossI = i < 0 || i >= INTERIOR;
        boolean acrossK = k < 0 || k >= INTERIOR;
        if (acrossI && acrossK) {
            return Blocks.POLISHED_BASALT.defaultBlockState();
        }
        int along = acrossI ? k : i;
        if (onBeam(along)) {
            return (dy == INTERIOR_HEIGHT ? Blocks.CHISELED_DEEPSLATE : Blocks.POLISHED_BASALT).defaultBlockState();
        }
        if (dy == 1 || dy == INTERIOR_HEIGHT) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        if (along == CENTRE && !doorWall) {
            if (dy >= SLIT_FROM && dy <= SLIT_TO) {
                return Blocks.TINTED_GLASS.defaultBlockState();
            }
            if (dy == SLIT_FROM - 1 || dy == SLIT_TO + 1) {
                return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
            }
        }
        return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    }

    private static @Nullable BlockState doorwayAt(int dy, int k) {
        int off = Math.abs(k - DOOR_K);
        if (off <= 1) {
            if (dy <= DOOR_HEIGHT) {
                return Blocks.AIR.defaultBlockState();
            }
            return dy == DOOR_HEIGHT + 1 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : null;
        }
        return off == 2 && dy <= DOOR_HEIGHT + 1 ? Blocks.POLISHED_BASALT.defaultBlockState() : null;
    }

    private BlockState floorAt(int i, int k) {
        if (hasLadder() && Math.max(Math.abs(i - SHAFT_I), Math.abs(k - SHAFT_K)) == 1) {
            return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        }
        boolean border = i == 0 || k == 0 || i == INTERIOR - 1 || k == INTERIOR - 1;
        return (border || onBeam(i) || onBeam(k) ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES)
            .defaultBlockState();
    }

    private static BlockState ceilingAt(int i, int k) {
        boolean daisLight = Math.abs(i - CENTRE) == DAIS_LIGHT_OFFSET && Math.abs(k - CENTRE) == DAIS_LIGHT_OFFSET;
        return (daisLight ? Blocks.PEARLESCENT_FROGLIGHT : Blocks.DEEPSLATE_TILES).defaultBlockState();
    }

    private BlockState fittingAt(int i, int dy, int k) {
        if (dy <= DAIS_HEIGHT) {
            if (onDais(i, k)) {
                return daisAt(i, k);
            }
            if (Math.abs(i - CENTRE) <= 1 && Math.abs(k - CENTRE) == DAIS_RADIUS + 1) {
                return Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, k < CENTRE ? Direction.SOUTH : Direction.NORTH);
            }
            boolean benchWall = i == 0 || (i == INTERIOR - 1 && !this.door);
            if (benchWall && Math.abs(k - CENTRE) <= BENCH_HALF_LENGTH) {
                return Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, i == 0 ? Direction.WEST : Direction.EAST);
            }
            return Blocks.AIR.defaultBlockState();
        }
        if (k == CENTRE && (i == PILLAR_I[0] || i == PILLAR_I[1])) {
            return HexwrightBlocks.BINDSTONE_COLUMN_BLOCK.defaultBlockState();
        }
        if (dy == INTERIOR_HEIGHT) {
            if (onBeam(i) || onBeam(k)) {
                return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            }
            if (lanternAt(i, k)) {
                return Blocks.CHAIN.defaultBlockState();
            }
        }
        if (dy == INTERIOR_HEIGHT - 1 && lanternAt(i, k)) {
            return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState daisAt(int i, int k) {
        int fromCentre = Math.abs(i - CENTRE) + Math.abs(k - CENTRE);
        if (fromCentre == 0) {
            return HexwrightBlocks.BINDSTONE_COLUMN_BLOCK.defaultBlockState();
        }
        if (fromCentre == 1) {
            return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        }
        boolean rim = !onDais(i - 1, k) || !onDais(i + 1, k) || !onDais(i, k - 1) || !onDais(i, k + 1);
        return (rim ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState();
    }

    private static boolean onDais(int i, int k) {
        int di = i - CENTRE;
        int dk = k - CENTRE;
        return di * di + dk * dk <= DAIS_RADIUS * (DAIS_RADIUS + 1);
    }

    private static boolean onBeam(int line) {
        return line == BEAMS[0] || line == BEAMS[1];
    }

    private static boolean lanternAt(int i, int k) {
        return isBayMiddle(i) && isBayMiddle(k) && !(i == CENTRE && k == CENTRE);
    }

    private static boolean isBayMiddle(int line) {
        return line == BAY_MIDDLES[0] || line == BAY_MIDDLES[1] || line == BAY_MIDDLES[2];
    }
}
