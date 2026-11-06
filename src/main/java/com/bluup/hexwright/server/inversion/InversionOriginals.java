package com.bluup.hexwright.server.inversion;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class InversionOriginals {

    record Original(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity) {
    }

    private final int minY;
    private final List<BlockState> palette = new ArrayList<>();
    private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
    private final Map<Long, Chunk> chunks = new HashMap<>();

    InversionOriginals(int minY) {
        this.minY = minY;
    }

    private static final class Chunk {
        final long[] done = new long[4];
        final int[] start = new int[256];
        final int[] count = new int[256];
        int[] packed = new int[64];
        int[] states = new int[64];
        int size;
        final Map<Integer, CompoundTag> blockEntities = new HashMap<>();

        boolean isDone(int column) {
            return (done[column >> 6] & 1L << (column & 63)) != 0;
        }

        void setDone(int column, boolean value) {
            if (value) {
                done[column >> 6] |= 1L << (column & 63);
            } else {
                done[column >> 6] &= ~(1L << (column & 63));
            }
        }

        boolean empty() {
            return done[0] == 0 && done[1] == 0 && done[2] == 0 && done[3] == 0;
        }

        void add(int pos, int state) {
            if (size == packed.length) {
                packed = Arrays.copyOf(packed, size * 2);
                states = Arrays.copyOf(states, size * 2);
            }
            packed[size] = pos;
            states[size] = state;
            size++;
        }
    }

    private static int column(int x, int z) {
        return (z & 15) << 4 | (x & 15);
    }

    boolean isDone(int x, int z) {
        Chunk chunk = chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
        return chunk != null && chunk.isDone(column(x, z));
    }

    void swallowed(int x, int z, List<Original> taken) {
        Chunk chunk = chunks.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), key -> new Chunk());
        int column = column(x, z);
        chunk.start[column] = chunk.size;
        chunk.count[column] = taken.size();
        for (Original original : taken) {
            int packed = (original.pos().getY() - minY) << 8 | column;
            chunk.add(packed, paletteIndex.computeIfAbsent(original.state(), state -> {
                palette.add(state);
                return palette.size() - 1;
            }));
            if (original.blockEntity() != null) {
                chunk.blockEntities.put(packed, original.blockEntity());
            }
        }
        chunk.setDone(column, true);
    }

    List<Original> giveBack(int x, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        Chunk chunk = chunks.get(key);
        int column = column(x, z);
        if (chunk == null || !chunk.isDone(column)) {
            return List.of();
        }
        List<Original> back = new ArrayList<>(chunk.count[column]);
        for (int i = chunk.start[column]; i < chunk.start[column] + chunk.count[column]; i++) {
            int packed = chunk.packed[i];
            BlockPos pos = new BlockPos(x, (packed >>> 8) + minY, z);
            int state = chunk.states[i];
            back.add(new Original(pos, state < palette.size() ? palette.get(state) : Blocks.AIR.defaultBlockState(),
                chunk.blockEntities.get(packed)));
        }
        chunk.setDone(column, false);
        if (chunk.empty()) {
            chunks.remove(key);
        }
        return back;
    }

    boolean isEmpty() {
        return chunks.isEmpty();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MinY", minY);
        ListTag states = new ListTag();
        for (BlockState state : palette) {
            states.add(NbtUtils.writeBlockState(state));
        }
        tag.put("Palette", states);
        ListTag saved = new ListTag();
        for (Map.Entry<Long, Chunk> entry : chunks.entrySet()) {
            Chunk chunk = entry.getValue();
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putLong("Pos", entry.getKey());
            chunkTag.put("Done", new LongArrayTag(chunk.done));
            chunkTag.putIntArray("Start", chunk.start);
            chunkTag.putIntArray("Count", chunk.count);
            chunkTag.putIntArray("Packed", Arrays.copyOf(chunk.packed, chunk.size));
            chunkTag.putIntArray("States", Arrays.copyOf(chunk.states, chunk.size));
            ListTag entities = new ListTag();
            for (Map.Entry<Integer, CompoundTag> blockEntity : chunk.blockEntities.entrySet()) {
                CompoundTag entityTag = new CompoundTag();
                entityTag.putInt("At", blockEntity.getKey());
                entityTag.put("Tag", blockEntity.getValue());
                entities.add(entityTag);
            }
            chunkTag.put("BlockEntities", entities);
            saved.add(chunkTag);
        }
        tag.put("Chunks", saved);
        return tag;
    }

    static InversionOriginals load(CompoundTag tag, HolderGetter<Block> blocks) {
        InversionOriginals originals = new InversionOriginals(tag.getInt("MinY"));
        for (Tag state : tag.getList("Palette", Tag.TAG_COMPOUND)) {
            BlockState read = NbtUtils.readBlockState(blocks, (CompoundTag) state);
            if (read.isAir() && !((CompoundTag) state).getString("Name").endsWith("air")) {
                Hexwright.LOGGER.warn("An Inversion saved {}, which this server does not know; it comes back as air",
                    ((CompoundTag) state).getString("Name"));
            }
            originals.palette.add(read.isAir() ? Blocks.AIR.defaultBlockState() : read);
        }
        for (int i = 0; i < originals.palette.size(); i++) {
            originals.paletteIndex.putIfAbsent(originals.palette.get(i), i);
        }
        for (Tag saved : tag.getList("Chunks", Tag.TAG_COMPOUND)) {
            CompoundTag chunkTag = (CompoundTag) saved;
            Chunk chunk = new Chunk();
            long[] done = chunkTag.getLongArray("Done");
            System.arraycopy(done, 0, chunk.done, 0, Math.min(done.length, 4));
            int[] start = chunkTag.getIntArray("Start");
            int[] count = chunkTag.getIntArray("Count");
            System.arraycopy(start, 0, chunk.start, 0, Math.min(start.length, 256));
            System.arraycopy(count, 0, chunk.count, 0, Math.min(count.length, 256));
            chunk.packed = chunkTag.getIntArray("Packed");
            chunk.states = chunkTag.getIntArray("States");
            chunk.size = Math.min(chunk.packed.length, chunk.states.length);
            if (chunk.packed.length == 0) {
                chunk.packed = new int[64];
                chunk.states = new int[64];
            }
            for (Tag entity : chunkTag.getList("BlockEntities", Tag.TAG_COMPOUND)) {
                CompoundTag entityTag = (CompoundTag) entity;
                chunk.blockEntities.put(entityTag.getInt("At"), entityTag.getCompound("Tag"));
            }
            originals.chunks.put(chunkTag.getLong("Pos"), chunk);
        }
        return originals;
    }
}
