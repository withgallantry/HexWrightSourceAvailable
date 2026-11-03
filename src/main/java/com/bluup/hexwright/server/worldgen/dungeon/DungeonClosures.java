package com.bluup.hexwright.server.worldgen.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DungeonClosures extends SavedData {

    private static final String STORAGE_ID = "hexwright_dungeon_regenerations";
    private static final String TAG_DUNGEONS = "Dungeons";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_START = "Start";
    private static final String TAG_HOME = "Home";
    private static final String TAG_GENERATION = "Generation";
    private static final String TAG_NOT_BEFORE = "NotBefore";
    private static final String TAG_HELD_BACK = "HeldBack";
    private static final String TAG_EXTENT = "Extent";
    private static final String TAG_TARGET = "Target";

    public static final class Entry {

        final ResourceKey<Level> dimension;

        final long start;

        final long home;

        final int generation;

        @Nullable
        final BlockPos target;

        @Nullable
        BoundingBox extent;

        long notBefore;

        @Nullable
        String heldBack;

        private Entry(ResourceKey<Level> dimension, long start, long home, int generation, @Nullable BlockPos target) {
            this.dimension = dimension;
            this.start = start;
            this.home = home;
            this.generation = generation;
            this.target = target;
        }
    }

    private final Map<ResourceKey<Level>, Map<Long, Entry>> byDimension = new HashMap<>();

    public static DungeonClosures get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(DungeonClosures::load, DungeonClosures::new, STORAGE_ID);
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

    boolean note(ResourceKey<Level> dimension, long start, @Nullable BoundingBox extent) {
        Map<Long, Entry> entries = this.byDimension.computeIfAbsent(dimension, key -> new HashMap<>());
        Entry entry = entries.get(start);
        boolean added = entry == null;
        if (added) {
            entry = new Entry(dimension, start, start, 0, null);
            entries.put(start, entry);
            setDirty();
        }
        if (extent != null && !extent.equals(entry.extent)) {
            entry.extent = extent;
            setDirty();
        }
        return added;
    }

    void hasten(Entry entry) {
        entry.notBefore = 0L;
        entry.heldBack = null;
        setDirty();
    }

    void postpone(Entry entry, long until, String reason) {
        entry.notBefore = until;
        entry.heldBack = reason;
        setDirty();
    }

    void forget(Entry entry) {
        Map<Long, Entry> entries = this.byDimension.get(entry.dimension);
        if (entries != null && entries.remove(entry.start) != null) {
            setDirty();
        }
    }

    private static DungeonClosures load(CompoundTag tag) {
        DungeonClosures state = new DungeonClosures();
        ListTag dungeons = tag.getList(TAG_DUNGEONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < dungeons.size(); index++) {
            CompoundTag row = dungeons.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(row.getString(TAG_DIMENSION));
            if (id == null) {
                continue;
            }
            long start = row.getLong(TAG_START);
            long home = row.contains(TAG_HOME, Tag.TAG_LONG) ? row.getLong(TAG_HOME) : start;
            BlockPos target = row.contains(TAG_TARGET, Tag.TAG_LONG) ? BlockPos.of(row.getLong(TAG_TARGET)) : null;
            Entry entry = new Entry(ResourceKey.create(Registries.DIMENSION, id), start, home,
                row.getInt(TAG_GENERATION), target);
            state.byDimension.computeIfAbsent(entry.dimension, key -> new HashMap<>()).put(start, entry);
            entry.notBefore = row.getLong(TAG_NOT_BEFORE);
            if (row.contains(TAG_HELD_BACK, Tag.TAG_STRING)) {
                entry.heldBack = row.getString(TAG_HELD_BACK);
            }
            int[] box = row.getIntArray(TAG_EXTENT);
            if (box.length == 6) {
                entry.extent = new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag dungeons = new ListTag();
        for (Entry entry : entries()) {
            CompoundTag row = new CompoundTag();
            row.putString(TAG_DIMENSION, entry.dimension.location().toString());
            row.putLong(TAG_START, entry.start);
            row.putLong(TAG_HOME, entry.home);
            row.putInt(TAG_GENERATION, entry.generation);
            row.putLong(TAG_NOT_BEFORE, entry.notBefore);
            if (entry.heldBack != null) {
                row.putString(TAG_HELD_BACK, entry.heldBack);
            }
            if (entry.extent != null) {
                BoundingBox box = entry.extent;
                row.putIntArray(TAG_EXTENT,
                    new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
            }
            if (entry.target != null) {
                row.putLong(TAG_TARGET, entry.target.asLong());
            }
            dungeons.add(row);
        }
        tag.put(TAG_DUNGEONS, dungeons);
        return tag;
    }
}
