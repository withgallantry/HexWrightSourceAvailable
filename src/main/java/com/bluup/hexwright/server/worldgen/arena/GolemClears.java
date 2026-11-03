package com.bluup.hexwright.server.worldgen.arena;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GolemClears extends SavedData {

    private static final String STORAGE_ID = "hexwright_golem_clears";
    private static final String TAG_CLEARED = "Cleared";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_START = "Start";
    private static final String TAG_BOX = "Box";
    private static final String TAG_CLEARED_AT = "ClearedAt";
    private static final String TAG_DUE = "Due";
    private static final String TAG_HELD_BACK = "HeldBack";

    private static final String TAG_LEGACY_ARENAS = "Arenas";
    private static final String TAG_LEGACY_STARTS = "Starts";

    public static final class Entry {

        final ResourceKey<Level> dimension;

        final long start;

        BoundingBox box;

        long clearedAt;

        long due;

        @Nullable
        String heldBack;

        private Entry(ResourceKey<Level> dimension, long start, BoundingBox box, long clearedAt, long due) {
            this.dimension = dimension;
            this.start = start;
            this.box = box;
            this.clearedAt = clearedAt;
            this.due = due;
        }
    }

    private final Map<ResourceKey<Level>, Map<Long, Entry>> byDimension = new HashMap<>();

    public static GolemClears get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(GolemClears::load, GolemClears::new, STORAGE_ID);
    }

    public boolean isCleared(ResourceKey<Level> dimension, long startChunk) {
        return find(dimension, startChunk) != null;
    }

    @Nullable
    Entry find(ResourceKey<Level> dimension, long start) {
        Map<Long, Entry> entries = this.byDimension.get(dimension);
        return entries == null ? null : entries.get(start);
    }

    List<Entry> entries() {
        List<Entry> all = new ArrayList<>();
        this.byDimension.values().forEach(entries -> all.addAll(entries.values()));
        return all;
    }

    void clear(ResourceKey<Level> dimension, long start, BoundingBox box, long due) {
        long now = System.currentTimeMillis();
        this.byDimension.computeIfAbsent(dimension, key -> new HashMap<>())
            .put(start, new Entry(dimension, start, box, now, due));
        setDirty();
    }

    void hasten(ResourceKey<Level> dimension, long start, BoundingBox box) {
        Entry entry = find(dimension, start);
        if (entry == null) {
            clear(dimension, start, box, 0L);
            return;
        }
        entry.box = box;
        entry.due = 0L;
        entry.heldBack = null;
        setDirty();
    }

    void postpone(Entry entry, long until, String reason) {
        entry.due = until;
        entry.heldBack = reason;
        setDirty();
    }

    void located(Entry entry, BoundingBox box) {
        if (!box.equals(entry.box)) {
            entry.box = box;
            setDirty();
        }
    }

    void forget(Entry entry) {
        Map<Long, Entry> entries = this.byDimension.get(entry.dimension);
        if (entries != null && entries.remove(entry.start) != null) {
            setDirty();
        }
    }

    private static GolemClears load(CompoundTag tag) {
        GolemClears state = new GolemClears();
        ListTag cleared = tag.getList(TAG_CLEARED, Tag.TAG_COMPOUND);
        for (int index = 0; index < cleared.size(); index++) {
            CompoundTag row = cleared.getCompound(index);
            ResourceKey<Level> dimension = dimension(row);
            int[] box = row.getIntArray(TAG_BOX);
            if (dimension == null || box.length != 6) {
                continue;
            }
            long start = row.getLong(TAG_START);
            Entry entry = new Entry(dimension, start,
                new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]),
                row.getLong(TAG_CLEARED_AT), row.getLong(TAG_DUE));
            if (row.contains(TAG_HELD_BACK, Tag.TAG_STRING)) {
                entry.heldBack = row.getString(TAG_HELD_BACK);
            }
            state.byDimension.computeIfAbsent(dimension, key -> new HashMap<>()).put(start, entry);
        }
        loadLegacy(state, tag.getList(TAG_LEGACY_ARENAS, Tag.TAG_COMPOUND));
        return state;
    }

    private static void loadLegacy(GolemClears state, ListTag arenas) {
        long now = System.currentTimeMillis();
        for (int index = 0; index < arenas.size(); index++) {
            CompoundTag row = arenas.getCompound(index);
            ResourceKey<Level> dimension = dimension(row);
            if (dimension == null) {
                continue;
            }
            for (long start : row.getLongArray(TAG_LEGACY_STARTS)) {
                ChunkPos chunk = new ChunkPos(start);
                BoundingBox reach = new BoundingBox(chunk.getMinBlockX(), -64, chunk.getMinBlockZ(),
                    chunk.getMinBlockX() + ArenaStructure.LENGTH - 1, 320,
                    chunk.getMinBlockZ() + ArenaStructure.LENGTH - 1);
                state.byDimension.computeIfAbsent(dimension, key -> new HashMap<>())
                    .putIfAbsent(start, new Entry(dimension, start, reach, now, now));
            }
        }
        if (!arenas.isEmpty()) {
            state.setDirty();
        }
    }

    private static @Nullable ResourceKey<Level> dimension(CompoundTag row) {
        ResourceLocation id = ResourceLocation.tryParse(row.getString(TAG_DIMENSION));
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag cleared = new ListTag();
        for (Entry entry : entries()) {
            CompoundTag row = new CompoundTag();
            row.putString(TAG_DIMENSION, entry.dimension.location().toString());
            row.putLong(TAG_START, entry.start);
            BoundingBox box = entry.box;
            row.putIntArray(TAG_BOX, new int[]{
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
            row.putLong(TAG_CLEARED_AT, entry.clearedAt);
            row.putLong(TAG_DUE, entry.due);
            if (entry.heldBack != null) {
                row.putString(TAG_HELD_BACK, entry.heldBack);
            }
            cleared.add(row);
        }
        tag.put(TAG_CLEARED, cleared);
        return tag;
    }
}
