package com.bluup.hexwright.server.maintenance;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.hexahedron.Hexahedron;
import com.bluup.hexwright.server.hexahedron.HexahedronStore;
import com.bluup.hexwright.server.inversion.Inversion;
import com.bluup.hexwright.server.inversion.InversionStore;
import com.bluup.hexwright.server.worldgen.quota.PlayableArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class KeystoneSites {

    private static final int CANDIDATES = 10;
    private static final double NEAREST = 384.0;
    private static final double FURTHEST = 1024.0;
    private static final int SAMPLE_STEP = 4;
    private static final long WATER = 20L * 60L * 20L;
    private static final double APART = 128.0;

    private KeystoneSites() {
    }

    static CompletableFuture<@Nullable BlockPos> find(ServerLevel level, BlockPos near, int radius, RandomSource random) {
        PlayableArea area = PlayableArea.of(level);
        List<BlockPos> spots = new ArrayList<>();
        List<CompletableFuture<Long>> lived = new ArrayList<>();
        for (int i = 0; i < CANDIDATES; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = NEAREST + random.nextDouble() * (FURTHEST - NEAREST);
            int x = Mth.floor(near.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(near.getZ() + Math.sin(angle) * distance);
            if ((area != null && !area.contains(x + 0.5, z + 0.5)) || crowded(level, x, z, radius)) {
                continue;
            }
            long wet = wet(level, x, z) ? WATER : 0L;
            spots.add(new BlockPos(x, 0, z));
            lived.add(livedIn(level, x, z, radius).thenApply(ticks -> ticks + wet));
        }
        if (spots.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(lived.toArray(CompletableFuture[]::new)).thenApply(done -> {
            int best = 0;
            for (int i = 1; i < spots.size(); i++) {
                if (lived.get(i).join() < lived.get(best).join()) {
                    best = i;
                }
            }
            if (HexwrightDebug.on(HexwrightDebug.MAINTENANCE)) {
                StringBuilder scores = new StringBuilder();
                for (int i = 0; i < spots.size(); i++) {
                    scores.append(' ').append(spots.get(i).getX()).append(',').append(spots.get(i).getZ())
                        .append('=').append(lived.get(i).join() / 20L).append('s');
                }
                HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] keystone sites lived in:{}; taking {}",
                    scores, spots.get(best).toShortString());
            }
            return spots.get(best);
        });
    }

    private static boolean crowded(ServerLevel level, int x, int z, int radius) {
        for (Hexahedron other : HexahedronStore.in(level)) {
            if (Math.hypot(other.x() - x, other.z() - z) < radius + other.radius() + APART) {
                return true;
            }
        }
        for (Inversion inversion : InversionStore.in(level)) {
            if (Math.hypot(inversion.x() - x, inversion.z() - z) < radius + Inversion.HALF + APART) {
                return true;
            }
        }
        return false;
    }

    private static boolean wet(ServerLevel level, int x, int z) {
        Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER);
    }

    private static CompletableFuture<Long> livedIn(ServerLevel level, int x, int z, int radius) {
        ChunkPos middle = new ChunkPos(new BlockPos(x, 0, z));
        int reach = radius >> 4;
        List<CompletableFuture<Long>> chunks = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx += SAMPLE_STEP) {
            for (int dz = -reach; dz <= reach; dz += SAMPLE_STEP) {
                if (dx * dx + dz * dz <= reach * reach) {
                    chunks.add(inhabited(level, new ChunkPos(middle.x + dx, middle.z + dz)));
                }
            }
        }
        return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new))
            .thenApply(done -> chunks.stream().mapToLong(CompletableFuture::join).max().orElse(0L));
    }

    private static CompletableFuture<Long> inhabited(ServerLevel level, ChunkPos chunk) {
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded.getInhabitedTime());
        }
        return level.getChunkSource().chunkMap.read(chunk)
            .thenApply(saved -> saved.map(tag -> tag.getLong("InhabitedTime")).orElse(0L))
            .exceptionally(error -> 0L);
    }
}
