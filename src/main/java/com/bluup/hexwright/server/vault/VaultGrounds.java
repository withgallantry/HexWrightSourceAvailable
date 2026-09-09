package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class VaultGrounds {

    public static final int BASE_Y = 8;

    public static final int GROUND_Y = 11;

    public static final int PLANE_SIZE = 64;

    private static final int WALL_TOP_Y = 14;

    private static final int LID_CLEARANCE = 8;

    private static final int PATH_HALF_WIDTH = 2;

    private VaultGrounds() {
    }

    public static int maxBuildTop() {
        return VaultDimension.HEIGHT - 2 - LID_CLEARANCE;
    }

    static void generate(ServerLevel level, VaultRecord record) {
        VaultBuild build = VaultBuilds.of(record);
        if (build.topY() > maxBuildTop()) {
            Hexwright.LOGGER.error("Vault build '{}' reaches y={}, above the {} the vault dimension leaves it",
                build.id(), build.topY(), maxBuildTop());
        }
        BlockPos min = VaultRooms.roomOrigin(record);
        RandomSource random = RandomSource.create(record.id() * 0x9E3779B97F4A7C15L);
        Plot plot = new Plot(level, min, build.groundsSize());
        int lidY = Math.min(build.topY() + LID_CLEARANCE, VaultDimension.HEIGHT - 1);

        groundwork(plot);
        rampart(plot, lidY);
        lake(plot, build, random);
        path(plot, build);
        build.place(level, min, record, random);
        planting(plot, build, random);
        VaultRooms.generateArch(level, min, VaultRooms.planOf(record));
    }

    static void generatePlane(ServerLevel level, VaultRecord record) {
        BlockPos min = VaultRooms.roomOrigin(record);
        Plot plot = new Plot(level, min, PLANE_SIZE);
        groundwork(plot);
        rampart(plot, VaultDimension.HEIGHT - 1);
        VaultRooms.generateArch(level, min, VaultRooms.planOf(record));
    }

    private static void groundwork(Plot plot) {
        BlockState base = HexwrightBlocks.VAULT_FLOOR_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int x = 0; x < plot.size(); x++) {
            for (int z = 0; z < plot.size(); z++) {
                plot.set(x, BASE_Y, z, base);
                plot.set(x, BASE_Y + 1, z, dirt);
                plot.set(x, BASE_Y + 2, z, dirt);
                plot.set(x, GROUND_Y, z, grass);
            }
        }
    }

    private static void rampart(Plot plot, int lidY) {
        BlockState wall = HexwrightBlocks.VAULT_SHELL_BLOCK.defaultBlockState();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        int size = plot.size();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                boolean edge = x == 0 || x == size - 1 || z == 0 || z == size - 1;
                if (edge) {
                    for (int y = BASE_Y; y < WALL_TOP_Y; y++) {
                        plot.set(x, y, z, wall);
                    }
                    if (((x + z) & 1) == 0) {
                        plot.set(x, WALL_TOP_Y, z, wall);
                    }
                    for (int y = WALL_TOP_Y + 1; y < lidY; y++) {
                        plot.set(x, y, z, barrier);
                    }
                }
                plot.set(x, lidY, z, barrier);
            }
        }
    }


    private static void lake(Plot plot, VaultBuild build, RandomSource random) {
        Lake lake = lakeFor(plot.size(), build);
        if (lake == null) {
            return;
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        for (int x = 0; x < plot.size(); x++) {
            for (int z = 0; z < plot.size(); z++) {
                double edge = lake.edge(x, z);
                if (edge > 1.0) {
                    continue;
                }
                if (edge > 0.82) {
                    plot.set(x, GROUND_Y, z, random.nextInt(4) == 0 ? gravel : sand);
                    continue;
                }
                plot.set(x, GROUND_Y, z, water);
                plot.set(x, GROUND_Y - 1, z, water);
                plot.set(x, GROUND_Y - 2, z, edge > 0.6 ? sand : gravel);
            }
        }
        for (int attempt = 0; attempt < 80; attempt++) {
            int x = (int) (lake.centreX() - lake.radiusX() - 2) + random.nextInt((int) lake.radiusX() * 2 + 5);
            int z = (int) (lake.centreZ() - lake.radiusZ() - 2) + random.nextInt((int) lake.radiusZ() * 2 + 5);
            if (!plot.inBounds(x, z)) {
                continue;
            }
            double edge = lake.edge(x, z);
            if (edge < 0.75 && random.nextInt(3) == 0) {
                plot.set(x, GROUND_Y + 1, z, Blocks.LILY_PAD.defaultBlockState());
            } else if (edge > 0.82 && edge <= 1.0 && random.nextInt(4) == 0 && plot.touchesWater(x, GROUND_Y, z)) {
                plot.set(x, GROUND_Y, z, sand);
                int height = 2 + random.nextInt(2);
                for (int y = 1; y <= height; y++) {
                    plot.set(x, GROUND_Y + y, z, Blocks.SUGAR_CANE.defaultBlockState());
                }
            }
        }
    }

    private static @Nullable Lake lakeFor(int size, VaultBuild build) {
        int southBand = size - 2 - build.reserved().maxZ();
        int northBand = build.reserved().minZ() - 2;
        boolean south = southBand >= northBand;
        int band = Math.max(south ? southBand : northBand, 0);
        double centreZ = south ? build.reserved().maxZ() + 1 + band / 2.0 : 1 + band / 2.0;
        double radiusZ = (band / 2.0 - 2.0) / WOBBLE_REACH;
        if (radiusZ < 3.0) {
            return null;
        }
        double centreX = size * 0.28;
        double radiusX = Math.min(size * 0.16, (centreX - 2.0) / WOBBLE_REACH);
        return new Lake(centreX, centreZ, radiusX, radiusZ);
    }

    private static final double WOBBLE_REACH = 1.22;

    private record Lake(double centreX, double centreZ, double radiusX, double radiusZ) {

        double edge(int x, int z) {
            double dx = (x - centreX) / radiusX;
            double dz = (z - centreZ) / radiusZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 1.6) {
                return 2.0;
            }
            double angle = Math.atan2(dz, dx);
            double wobble = 1.0 + 0.14 * Math.sin(angle * 3.0) + 0.08 * Math.cos(angle * 5.0 + 1.3);
            return distance / wobble;
        }
    }

    private static void path(Plot plot, VaultBuild build) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        int centreX = pathCentreX(plot.size());
        int doorZ = Math.min(build.entrance().getZ(), build.reserved().minZ() - 1);
        for (int z = VaultRooms.PORTAL_INSET; z <= doorZ; z++) {
            for (int x = centreX - PATH_HALF_WIDTH; x <= centreX + PATH_HALF_WIDTH - 1; x++) {
                plot.set(x, GROUND_Y, z, ((x * 7 + z * 3) & 3) == 0 ? mossy : stone);
            }
        }
        for (int step = 1; step <= 2; step++) {
            int z = VaultRooms.PORTAL_INSET + Math.max(doorZ - VaultRooms.PORTAL_INSET, 0) * step / 3;
            for (int x : new int[]{centreX - PATH_HALF_WIDTH - 2, centreX + PATH_HALF_WIDTH + 1}) {
                plot.set(x, GROUND_Y + 1, z, Blocks.OAK_FENCE.defaultBlockState());
                plot.set(x, GROUND_Y + 2, z, Blocks.OAK_FENCE.defaultBlockState());
                plot.set(x, GROUND_Y + 3, z,
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
    }

    public static int pathCentreX(int groundsSize) {
        return groundsSize / 2;
    }

    private static final int MIN_LAWN = 8;

    public static int alignedOriginX(int groundsSize, int buildWidth, int entranceX) {
        int ideal = pathCentreX(groundsSize) - entranceX;
        int latest = Math.max(groundsSize - buildWidth - MIN_LAWN, MIN_LAWN);
        return Mth.clamp(ideal, MIN_LAWN, latest);
    }


    private static final ResourceKey<ConfiguredFeature<?, ?>>[] TREES = trees();

    @SuppressWarnings("unchecked")
    private static ResourceKey<ConfiguredFeature<?, ?>>[] trees() {
        String[] names = {"oak", "fancy_oak", "birch", "spruce", "oak"};
        ResourceKey<ConfiguredFeature<?, ?>>[] keys = new ResourceKey[names.length];
        for (int i = 0; i < names.length; i++) {
            keys[i] = ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation(names[i]));
        }
        return keys;
    }

    private static final BlockState[] FLOWERS = {
        Blocks.POPPY.defaultBlockState(),
        Blocks.DANDELION.defaultBlockState(),
        Blocks.AZURE_BLUET.defaultBlockState(),
        Blocks.OXEYE_DAISY.defaultBlockState(),
        Blocks.CORNFLOWER.defaultBlockState(),
        Blocks.ALLIUM.defaultBlockState()
    };

    private static void planting(Plot plot, VaultBuild build, RandomSource random) {
        Lake lake = lakeFor(plot.size(), build);
        int trees = plot.size() * plot.size() / 200;
        List<int[]> planted = new ArrayList<>();
        for (int attempt = 0; attempt < trees * 14 && planted.size() < trees; attempt++) {
            int x = 4 + random.nextInt(plot.size() - 8);
            int z = 4 + random.nextInt(plot.size() - 8);
            if (!isOpenLawn(plot, build, lake, x, z, 3)) {
                continue;
            }
            boolean crowded = false;
            for (int[] other : planted) {
                int dx = other[0] - x;
                int dz = other[1] - z;
                if (dx * dx + dz * dz < 36) {
                    crowded = true;
                    break;
                }
            }
            if (crowded) {
                continue;
            }
            planted.add(new int[]{x, z});
            plot.tree(x, GROUND_Y + 1, z, TREES[random.nextInt(TREES.length)], random);
        }

        for (int x = 1; x < plot.size() - 1; x++) {
            for (int z = 1; z < plot.size() - 1; z++) {
                if (!isOpenLawn(plot, build, lake, x, z, 0) || random.nextInt(100) >= 22) {
                    continue;
                }
                if (!plot.isAir(x, GROUND_Y + 1, z) || !plot.isGrass(x, GROUND_Y, z)) {
                    continue;
                }
                plot.set(x, GROUND_Y + 1, z, random.nextInt(4) == 0
                    ? FLOWERS[random.nextInt(FLOWERS.length)]
                    : Blocks.GRASS.defaultBlockState());
            }
        }
    }

    private static boolean isOpenLawn(Plot plot, VaultBuild build,
                                      @Nullable Lake lake,
                                      int x, int z, int margin) {
        int size = plot.size();
        if (x - margin < 1 || z - margin < 1 || x + margin >= size - 1 || z + margin >= size - 1) {
            return false;
        }
        if (build.reserved().contains(x, z, margin + 1)) {
            return false;
        }
        int centreX = pathCentreX(size);
        boolean onApproach = Math.abs(x - centreX) <= PATH_HALF_WIDTH + 3 + margin
            && z <= build.entrance().getZ();
        if (onApproach) {
            return false;
        }
        return lake == null || lake.edge(x, z) > 1.0 + margin * 0.14;
    }


    private record Plot(ServerLevel level, BlockPos min, int size) {

        void set(int x, int y, int z, BlockState state) {
            level.setBlock(new BlockPos(min.getX() + x, y, min.getZ() + z), state, Block.UPDATE_CLIENTS);
        }

        boolean inBounds(int x, int z) {
            return x >= 0 && z >= 0 && x < size && z < size;
        }

        boolean isAir(int x, int y, int z) {
            return level.getBlockState(new BlockPos(min.getX() + x, y, min.getZ() + z)).isAir();
        }

        boolean isGrass(int x, int y, int z) {
            return level.getBlockState(new BlockPos(min.getX() + x, y, min.getZ() + z))
                .is(Blocks.GRASS_BLOCK);
        }

        boolean touchesWater(int x, int y, int z) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos neighbour = new BlockPos(min.getX() + x, y, min.getZ() + z).relative(side);
                if (level.getBlockState(neighbour).is(Blocks.WATER)) {
                    return true;
                }
            }
            return false;
        }

        void tree(int x, int y, int z, ResourceKey<ConfiguredFeature<?, ?>> key, RandomSource random) {
            BlockPos pos = new BlockPos(min.getX() + x, y, min.getZ() + z);
            ConfiguredFeature<?, ?> feature = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(key);
            if (feature != null && feature.place(level, level.getChunkSource().getGenerator(), random, pos)) {
                return;
            }
            Hexwright.LOGGER.debug("Vault grounds falling back to a plain tree at {} ({})", pos, key.location());
            plainTree(x, y, z, random);
        }

        private void plainTree(int x, int y, int z, RandomSource random) {
            int height = 4 + random.nextInt(3);
            for (int i = 0; i < height; i++) {
                set(x, y + i, z, Blocks.OAK_LOG.defaultBlockState());
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = height - 3; dy <= height; dy++) {
                        int spread = Math.abs(dx) + Math.abs(dz) + Math.abs(dy - height + 1);
                        if (spread > 3 || (dx == 0 && dz == 0 && dy < height)) {
                            continue;
                        }
                        if (!inBounds(x + dx, z + dz) || !isAir(x + dx, y + dy, z + dz)) {
                            continue;
                        }
                        set(x + dx, y + dy, z + dz, Blocks.OAK_LEAVES.defaultBlockState());
                    }
                }
            }
        }
    }
}
