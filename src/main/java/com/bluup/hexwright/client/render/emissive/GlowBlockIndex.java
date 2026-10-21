package com.bluup.hexwright.client.render.emissive;

import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class GlowBlockIndex {
    private static final Glow[] NONE = new Glow[0];
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Long2ObjectMap<Glow[]> NO_SECTIONS = Long2ObjectMaps.emptyMap();

    private static final Map<ClientLevel, LevelIndex> LEVELS = new WeakHashMap<>();

    @Nullable
    private static Set<BlockState> glowing;

    @Nullable
    private static Object bakery;

    private GlowBlockIndex() {
    }

    record Glow(BlockPos pos, BlockState state, BakedModel glow, @Nullable Transformation rotation, int faces) {
        boolean drawsFace(Direction direction) {
            return (faces & (1 << direction.get3DDataValue())) != 0;
        }
    }

    private static final class LevelIndex {
        final LongSet chunks = new LongOpenHashSet();
        final LongSet dirtyChunks = new LongOpenHashSet();
        final LongSet dirtySections = new LongOpenHashSet();
        final Long2ObjectMap<Glow[]> lit = new Long2ObjectOpenHashMap<>();
    }

    static void onChunkLoaded(ClientLevel level, ChunkPos chunk) {
        LevelIndex index = LEVELS.computeIfAbsent(level, ignored -> new LevelIndex());
        long key = chunk.toLong();
        index.chunks.add(key);
        index.dirtyChunks.add(key);
        markNeighbourEdges(level, index, chunk);
    }

    static void onChunkUnloaded(ClientLevel level, ChunkPos chunk) {
        LevelIndex index = LEVELS.get(level);
        if (index == null) {
            return;
        }
        long key = chunk.toLong();
        index.chunks.remove(key);
        index.dirtyChunks.remove(key);
        for (int sectionY = level.getMinSection(); sectionY < level.getMaxSection(); sectionY++) {
            long section = SectionPos.asLong(chunk.x, sectionY, chunk.z);
            index.lit.remove(section);
            index.dirtySections.remove(section);
        }
        markNeighbourEdges(level, index, chunk);
    }

    static void onBlockChanged(ClientLevel level, BlockPos pos) {
        LevelIndex index = LEVELS.get(level);
        if (index == null) {
            return;
        }
        long own = SectionPos.asLong(pos);
        index.dirtySections.add(own);
        for (Direction direction : DIRECTIONS) {
            long neighbour = SectionPos.asLong(
                SectionPos.blockToSectionCoord(pos.getX() + direction.getStepX()),
                SectionPos.blockToSectionCoord(pos.getY() + direction.getStepY()),
                SectionPos.blockToSectionCoord(pos.getZ() + direction.getStepZ()));
            if (neighbour != own && index.lit.containsKey(neighbour)) {
                index.dirtySections.add(neighbour);
            }
        }
    }

    static boolean anyGlowingBlocks() {
        return !glowing().isEmpty();
    }

    static Long2ObjectMap<Glow[]> litSections(ClientLevel level) {
        Set<BlockState> states = glowing();
        LevelIndex index = LEVELS.get(level);
        if (index == null) {
            return NO_SECTIONS;
        }
        if (!index.dirtyChunks.isEmpty()) {
            for (LongIterator it = index.dirtyChunks.iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                LevelChunk chunk = loadedChunk(level, ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    store(index, level, states, sections[i],
                        chunk.getPos().x, chunk.getSectionYFromSectionIndex(i), chunk.getPos().z);
                }
            }
            index.dirtyChunks.clear();
        }
        if (!index.dirtySections.isEmpty()) {
            for (LongIterator it = index.dirtySections.iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                int sectionX = SectionPos.x(key);
                int sectionY = SectionPos.y(key);
                int sectionZ = SectionPos.z(key);
                store(index, level, states, sectionAt(level, sectionX, sectionY, sectionZ),
                    sectionX, sectionY, sectionZ);
            }
            index.dirtySections.clear();
        }
        return index.lit;
    }

    private static void store(LevelIndex index, ClientLevel level, Set<BlockState> states,
                              @Nullable LevelChunkSection section, int sectionX, int sectionY, int sectionZ) {
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        Glow[] found = section == null ? NONE : scan(level, states, section, sectionX, sectionY, sectionZ);
        if (found.length == 0) {
            index.lit.remove(key);
        } else {
            index.lit.put(key, found);
        }
    }

    private static void markNeighbourEdges(ClientLevel level, LevelIndex index, ChunkPos chunk) {
        if (index.lit.isEmpty()) {
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int x = chunk.x + direction.getStepX();
            int z = chunk.z + direction.getStepZ();
            for (int sectionY = level.getMinSection(); sectionY < level.getMaxSection(); sectionY++) {
                long section = SectionPos.asLong(x, sectionY, z);
                if (index.lit.containsKey(section)) {
                    index.dirtySections.add(section);
                }
            }
        }
    }

    @Nullable
    private static LevelChunk loadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk loaded ? loaded : null;
    }

    @Nullable
    private static LevelChunkSection sectionAt(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
        LevelChunk loaded = loadedChunk(level, sectionX, sectionZ);
        if (loaded == null) {
            return null;
        }
        int index = loaded.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] sections = loaded.getSections();
        return index < 0 || index >= sections.length ? null : sections[index];
    }

    private static Glow[] scan(ClientLevel level, Set<BlockState> states, LevelChunkSection section,
                               int sectionX, int sectionY, int sectionZ) {
        if (states.isEmpty() || section.hasOnlyAir() || !section.maybeHas(states::contains)) {
            return NONE;
        }

        BlockModelShaper shaper = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
        int baseX = SectionPos.sectionToBlockCoord(sectionX);
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
        List<Glow> found = new ArrayList<>();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (!states.contains(state)) {
                        continue;
                    }
                    BakedModel model = shaper.getBlockModel(state);
                    BakedModel glow = BlockGlow.glowTwinOf(model);
                    if (glow == null) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                    found.add(new Glow(pos, state, glow, rotationOf(model), visibleFaces(level, state, pos)));
                }
            }
        }
        return found.toArray(NONE);
    }

    private static int visibleFaces(ClientLevel level, BlockState state, BlockPos pos) {
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        int faces = 0;
        for (Direction direction : DIRECTIONS) {
            neighbour.setWithOffset(pos, direction);
            if (Block.shouldRenderFace(state, level, pos, direction, neighbour)) {
                faces |= 1 << direction.get3DDataValue();
            }
        }
        return faces;
    }

    @Nullable
    private static Transformation rotationOf(BakedModel model) {
        return model instanceof EmissiveBakedModel emissive ? emissive.modelRotation() : null;
    }

    private static Set<BlockState> glowing() {
        ModelManager models = Minecraft.getInstance().getModelManager();
        Object current = models.getMissingModel();
        Set<BlockState> known = glowing;
        if (known != null && current == bakery) {
            return known;
        }
        for (LevelIndex index : LEVELS.values()) {
            index.lit.clear();
            index.dirtySections.clear();
            index.dirtyChunks.addAll(index.chunks);
        }

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
