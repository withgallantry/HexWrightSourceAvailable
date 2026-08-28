package com.bluup.hexwright.server.worldgen.ruinedportal;

import com.bluup.hexwright.server.block.Outcome;
import com.bluup.hexwright.server.block.RuinedPortalFrameBlockEntity;
import net.minecraft.core.BlockPos;
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

final class RuinedPortalPerils {

    private static final int MIN_RADIUS = 64;
    private static final int MAX_RADIUS = 400;
    private static final int SEARCH_ATTEMPTS = 48;

    private static final int DEEP_MIN_Y = 8;
    private static final int DEEP_MAX_Y = 45;

    private static final int NETHER_LAVA_Y = 27;

    private static final EntityType<?>[] DEN_MOBS = {
        EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CAVE_SPIDER
    };

    private RuinedPortalPerils() {
    }

    static RuinedPortalFrameBlockEntity.Binding conjure(ServerLevel origin, BlockPos portalPos, RandomSource random) {
        return switch (random.nextInt(7)) {
            case 0 -> lava(origin, portalPos, random);
            case 1 -> netherLava(origin, portalPos, random);
            case 2 -> deepOcean(origin, portalPos, random);
            case 3 -> deepDark(origin, portalPos, random);
            case 4 -> highFall(origin, portalPos, random);
            case 5 -> voidPocket(origin, portalPos, random);
            default -> mobDen(origin, portalPos, random);
        };
    }

    private static RuinedPortalFrameBlockEntity.Binding lava(ServerLevel level, BlockPos portalPos, RandomSource random) {
        BlockPos site = randomColumn(portalPos, random, DEEP_MIN_Y, DEEP_MAX_Y);
        fillPocket(level, site, 1, Blocks.LAVA.defaultBlockState());
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_LAVA, level.dimension(), site, null);
    }

    private static RuinedPortalFrameBlockEntity.Binding netherLava(ServerLevel origin, BlockPos portalPos, RandomSource random) {
        ServerLevel nether = origin.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            return lava(origin, portalPos, random);
        }
        BlockPos site = randomColumn(portalPos, random, NETHER_LAVA_Y, NETHER_LAVA_Y);
        fillPocket(nether, site, 1, Blocks.LAVA.defaultBlockState());
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_NETHER_LAVA, nether.dimension(), site, null);
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
        BlockPos site = randomColumn(portalPos, random, DEEP_MIN_Y, DEEP_MAX_Y);
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
        BlockPos site = randomColumn(portalPos, random, DEEP_MIN_Y - 20, DEEP_MIN_Y);
        carveRoom(level, site, 3, 3);
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_DEEP_DARK, level.dimension(), site, null);
    }

    private static RuinedPortalFrameBlockEntity.Binding highFall(ServerLevel level, BlockPos portalPos, RandomSource random) {
        BlockPos column = randomXZ(portalPos, random);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.getX(), column.getZ());
        BlockPos site = new BlockPos(column.getX(), surface + 40, column.getZ());
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_HIGH_FALL, level.dimension(), site, null);
    }

    private static RuinedPortalFrameBlockEntity.Binding voidPocket(ServerLevel level, BlockPos portalPos, RandomSource random) {
        BlockPos site = randomColumn(portalPos, random, DEEP_MIN_Y, DEEP_MAX_Y);
        carveRoom(level, site, 2, 2);
        return new RuinedPortalFrameBlockEntity.Binding(Outcome.PERIL_VOID_POCKET, level.dimension(), site, null);
    }

    private static RuinedPortalFrameBlockEntity.Binding mobDen(ServerLevel level, BlockPos portalPos, RandomSource random) {
        BlockPos site = randomColumn(portalPos, random, DEEP_MIN_Y, DEEP_MAX_Y);
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
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy < height; dy++) {
                    level.setBlock(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
