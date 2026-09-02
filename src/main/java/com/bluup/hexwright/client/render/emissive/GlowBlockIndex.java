package com.bluup.hexwright.client.render.emissive;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class GlowBlockIndex {
    private static final long[] NONE = new long[0];

    private static final int MAX_CACHED_SECTIONS = 8192;

    private static final Long2ObjectMap<long[]> SECTIONS = new Long2ObjectOpenHashMap<>();

    @Nullable
    private static Set<BlockState> glowing;

    @Nullable
    private static Object bakery;

    private GlowBlockIndex() {
    }

    static void invalidate(BlockPos pos) {
        if (!SECTIONS.isEmpty()) {
            SECTIONS.remove(SectionPos.asLong(pos));
        }
    }

    static void invalidate(ChunkPos chunk) {
        if (SECTIONS.isEmpty()) {
            return;
        }
        for (int sectionY = -32; sectionY <= 32; sectionY++) {
            SECTIONS.remove(SectionPos.asLong(chunk.x, sectionY, chunk.z));
        }
    }

    static void clear() {
        SECTIONS.clear();
    }

    static boolean anyGlowingBlocks() {
        return !glowing().isEmpty();
    }

    static long[] positions(LevelReader level, int sectionX, int sectionY, int sectionZ) {
        if (sectionY < level.getMinSection() || sectionY >= level.getMaxSection()) {
            return NONE;
        }

        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        long[] cached = SECTIONS.get(key);
        if (cached != null) {
            return cached;
        }

        LevelChunkSection section = sectionAt(level, sectionX, sectionY, sectionZ);
        if (section == null) {
            return NONE;
        }

        long[] found = scan(section, sectionX, sectionY, sectionZ);
        if (SECTIONS.size() >= MAX_CACHED_SECTIONS) {
            SECTIONS.clear();
        }
        SECTIONS.put(key, found);
        return found;
    }

    @Nullable
    private static LevelChunkSection sectionAt(LevelReader level, int sectionX, int sectionY, int sectionZ) {
        ChunkAccess chunk = level.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk loaded)) {
            return null;
        }
        int index = loaded.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] sections = loaded.getSections();
        return index < 0 || index >= sections.length ? null : sections[index];
    }

    private static long[] scan(LevelChunkSection section, int sectionX, int sectionY, int sectionZ) {
        Set<BlockState> states = glowing();
        if (states.isEmpty() || section.hasOnlyAir() || !section.maybeHas(states::contains)) {
            return NONE;
        }

        int baseX = SectionPos.sectionToBlockCoord(sectionX);
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
        LongArrayList found = new LongArrayList();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (states.contains(section.getBlockState(x, y, z))) {
                        found.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
        }
        return found.isEmpty() ? NONE : found.toLongArray();
    }

    private static Set<BlockState> glowing() {
        ModelManager models = Minecraft.getInstance().getModelManager();
        Object current = models.getMissingModel();
        Set<BlockState> known = glowing;
        if (known != null && current == bakery) {
            return known;
        }
        SECTIONS.clear();

        Set<BlockState> found = Collections.newSetFromMap(new IdentityHashMap<>());
        BlockModelShaper shaper = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (state.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                if (BlockGlow.glowTwinOf(shaper.getBlockModel(state)) != null) {
                    found.add(state);
                }
            }
        }
        glowing = found;
        bakery = current;
        return found;
    }
}
