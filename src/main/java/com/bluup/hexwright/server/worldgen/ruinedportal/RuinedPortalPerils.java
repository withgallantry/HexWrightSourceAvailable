package com.bluup.hexwright.server.worldgen.ruinedportal;

import com.bluup.hexwright.server.block.Outcome;
import com.bluup.hexwright.server.block.RuinedPortalFrameBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

final class RuinedPortalPerils {

    private static final int MIN_RADIUS = 64;
    private static final int MAX_RADIUS = 400;
    private static final int SEARCH_ATTEMPTS = 48;

    private static final int DEEP_FLOOR_OFFSET = 12;
    private static final int DEEP_BAND = 44;

    private static final int HIGH_FALL_MIN = 40;
    private static final int HIGH_FALL_EXTRA = 40;

    private static final int NETHER_LEDGE_MAX_Y = 120;
    private static final int NETHER_FALLBACK_Y = 70;
    private static final int NETHER_ATTEMPTS = 6;

    private static final EntityType<?>[] DEN_MOBS = {
        EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CAVE_SPIDER
    };

    private RuinedPortalPerils() {
    }

    static RuinedPortalFrameBlockEntity.Binding conjure(ServerLevel origin, BlockPos portalPos, RandomSource random) {
        return switch (random.nextInt(8)) {
            case 0, 1 -> highFall(origin, portalPos, random);
            case 2, 3 -> nether(origin, portalPos, random);
            case 4 -> deepOcean(origin, portalPos, random);
            case 5 -> deepDark(origin, portalPos, random);
            case 6 -> voidPocket(origin, portalPos, random);
            default -> mobDen(origin, portalPos, random);
        };
    }

    private static RuinedPortalFrameBlockEntity.Binding nether(ServerLevel origin, BlockPos portalPos, RandomSource random) {
        ServerLevel nether = origin.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            return voidPocket(origin, portalPos, random);
        }
        for (int attempt = 0; attempt < NETHER_ATTEMPTS; attempt++) {
            BlockPos ledge = netherLedge(nether, randomXZ(portalPos, random), random);
            if (ledge != null) {
                return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_NETHER, nether.dimension(), ledge, null);
            }
        }
        BlockPos site = randomColumn(portalPos, random, NETHER_FALLBACK_Y, NETHER_FALLBACK_Y);
        carveRoom(nether, site, 2, 3);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                nether.setBlock(site.offset(dx, -1, dz), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_NETHER, nether.dimension(), site, null);
    }

    private static BlockPos netherLedge(ServerLevel nether, BlockPos column, RandomSource random) {
        nether.getChunk(column.getX() >> 4, column.getZ() >> 4);
        List<BlockPos> ledges = new ArrayList<>();
        for (int y = nether.getSeaLevel() + 2; y <= NETHER_LEDGE_MAX_Y; y++) {
            BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
            if (standable(nether, feet)) {
                ledges.add(feet);
            }
        }
        return ledges.isEmpty() ? null : ledges.get(random.nextInt(ledges.size()));
    }

    private static RuinedPortalFrameBlockEntity.Binding deepOcean(ServerLevel level, BlockPos portalPos, RandomSource random) {
        int seaLevel = level.getSeaLevel();
        for (int attempt = 0; attempt < SEARCH_ATTEMPTS; attempt++) {
            BlockPos column = randomXZ(portalPos, random);
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, column.getX(), column.getZ());
            if (seaLevel - floor < 12) {
                continue;
            }
            BlockPos surface = new BlockPos(column.getX(), seaLevel - 1, column.getZ());
            if (!level.getFluidState(surface).is(FluidTags.WATER)) {
                continue;
            }
            BlockPos site = new BlockPos(column.getX(), seaLevel - 4, column.getZ());
            return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_DEEP_OCEAN, level.dimension(), site, null);
        }
        BlockPos site = deepColumn(level, portalPos, random);
        fillPocket(level, site, 2, Blocks.WATER.defaultBlockState());
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_DEEP_OCEAN, level.dimension(), site.above(), null);
    }

    private static RuinedPortalFrameBlockEntity.Binding deepDark(ServerLevel level, BlockPos portalPos, RandomSource random) {
        var found = level.findClosestBiome3d(
            holder -> holder.is(Biomes.DEEP_DARK), portalPos, MAX_RADIUS, 32, 8);
        if (found != null) {
            return new RuinedPortalFrameBlockEntity.Binding(
                Outcome.PERIL_DEEP_DARK, level.dimension(), found.getFirst().above(), null);
        }
        BlockPos site = deepColumn(level, portalPos, random);
        carveRoom(level, site, 3, 3);
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_DEEP_DARK, level.dimension(), site, null);
    }

    private static RuinedPortalFrameBlockEntity.Binding highFall(ServerLevel level, BlockPos portalPos, RandomSource random) {
        BlockPos column = randomXZ(portalPos, random);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.getX(), column.getZ());
        int height = HIGH_FALL_MIN + random.nextInt(HIGH_FALL_EXTRA + 1);
        BlockPos site = new BlockPos(column.getX(), Math.min(surface + height, level.getMaxBuildHeight() - 2), column.getZ());
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_HIGH_FALL, level.dimension(), site, null);
    }

    private static RuinedPortalFrameBlockEntity.Binding voidPocket(ServerLevel level, BlockPos portalPos, RandomSource random) {
        BlockPos site = deepColumn(level, portalPos, random);
        carveRoom(level, site, 2, 2);
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_VOID_POCKET, level.dimension(), site, null);
    }

    private static RuinedPortalFrameBlockEntity.Binding mobDen(ServerLevel level, BlockPos portalPos, RandomSource random) {
        BlockPos site = deepColumn(level, portalPos, random);
        carveRoom(level, site, 3, 3);
        spawnDen(level, site, random);
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_MOB_DEN, level.dimension(), site, null);
    }

    private static void spawnDen(ServerLevel level, BlockPos centre, RandomSource random) {
        int count = 4 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            EntityType<?> type = DEN_MOBS[random.nextInt(DEN_MOBS.length)];
            if (!(type.create(level) instanceof Monster monster)) {
                continue;
            }
            double x = centre.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double z = centre.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            monster.moveTo(x, centre.getY(), z, random.nextFloat() * 360.0f, 0.0f);
            monster.finalizeSpawn(level, level.getCurrentDifficultyAt(centre), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(monster);
        }
    }

    private static BlockPos randomXZ(BlockPos portalPos, RandomSource random) {
        double angle = random.nextDouble() * 2 * Math.PI;
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS);
        int x = portalPos.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = portalPos.getZ() + (int) Math.round(Math.sin(angle) * radius);
        return new BlockPos(x, 0, z);
    }

    private static BlockPos randomColumn(BlockPos portalPos, RandomSource random, int minY, int maxY) {
        BlockPos xz = randomXZ(portalPos, random);
        int y = minY >= maxY ? minY : minY + random.nextInt(maxY - minY + 1);
        return new BlockPos(xz.getX(), y, xz.getZ());
    }

    private static BlockPos deepColumn(ServerLevel level, BlockPos portalPos, RandomSource random) {
        int floor = level.getMinBuildHeight() + DEEP_FLOOR_OFFSET;
        return randomColumn(portalPos, random, floor, floor + DEEP_BAND);
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
            && open(level, feet) && open(level, feet.above());
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static void fillPocket(ServerLevel level, BlockPos centre, int radius, BlockState fluidState) {
        level.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos pos = centre.offset(dx, 0, dz);
                level.setBlock(pos, fluidState, Block.UPDATE_CLIENTS);
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void carveRoom(ServerLevel level, BlockPos centre, int radius, int height) {
        level.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                for (int dy = -1; dy <= height; dy++) {
                    BlockPos pos = centre.offset(dx, dy, dz);
                    boolean shell = Math.abs(dx) > radius || Math.abs(dz) > radius || dy < 0 || dy == height;
                    if (!shell) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                        level.setBlock(pos, sealFor(level, pos), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static BlockState sealFor(ServerLevel level, BlockPos pos) {
        if (level.dimension() == Level.NETHER) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        return pos.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }
}
