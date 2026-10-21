package com.bluup.hexwright.server.worldgen.bindstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BindstoneRegenerations extends SavedData {

    private static final String STORAGE_ID = "hexwright_bindstone_regenerations";
    private static final String TAG_PILLARS = "Pillars";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_START = "Start";
    private static final String TAG_HOME = "Home";
    private static final String TAG_CENTRE = "Centre";
    private static final String TAG_CLAIMED = "Claimed";
    private static final String TAG_MOVES = "Moves";
    private static final String TAG_NOT_BEFORE = "NotBefore";
    private static final String TAG_HELD_BACK = "HeldBack";

    public static final class Entry {

        final ResourceKey<Level> dimension;

        final long home;

        long start;

        BlockPos centre;

        boolean claimed;

        int moves;

        long notBefore;

        @Nullable
        String heldBack;

        private Entry(ResourceKey<Level> dimension, long home, long start, BlockPos centre) {
            this.dimension = dimension;
            this.home = home;
            this.start = start;
            this.centre = centre;
        }
    }

    private final Map<ResourceKey<Level>, Map<Long, Entry>> byDimension = new HashMap<>();

    public static BindstoneRegenerations get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(BindstoneRegenerations::load, BindstoneRegenerations::new, STORAGE_ID);
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

    boolean noteClaimed(ResourceKey<Level> dimension, long start, BlockPos centre) {
        Entry entry = this.byDimension.computeIfAbsent(dimension, key -> new HashMap<>())
            .computeIfAbsent(start, key -> new Entry(dimension, key, key, centre));
        if (entry.claimed) {
            return false;
        }
        entry.claimed = true;
        entry.centre = centre;
        entry.notBefore = 0L;
        entry.heldBack = null;
        setDirty();
        return true;
    }

    void hasten(ResourceKey<Level> dimension, long start, BlockPos centre) {
        noteClaimed(dimension, start, centre);
        Entry entry = find(dimension, start);
        entry.notBefore = 0L;
        entry.heldBack = null;
        setDirty();
    }

    void postpone(Entry entry, long until, String reason) {
        entry.notBefore = until;
        entry.heldBack = reason;
        setDirty();
    }

    void finish(Entry entry, long start, BlockPos centre) {
        Map<Long, Entry> entries = this.byDimension.get(entry.dimension);
        entries.remove(entry.start);
        entry.start = start;
        entries.put(start, entry);
        entry.centre = centre;
        entry.claimed = false;
        entry.moves++;
        entry.notBefore = 0L;
        entry.heldBack = null;
        setDirty();
    }

    void forget(Entry entry) {
        Map<Long, Entry> entries = this.byDimension.get(entry.dimension);
        if (entries != null && entries.remove(entry.start) != null) {
            setDirty();
        }
    }

    private static BindstoneRegenerations load(CompoundTag tag) {
        BindstoneRegenerations state = new BindstoneRegenerations();
        ListTag pillars = tag.getList(TAG_PILLARS, Tag.TAG_COMPOUND);
        for (int index = 0; index < pillars.size(); index++) {
            CompoundTag row = pillars.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(row.getString(TAG_DIMENSION));
            if (id == null) {
                continue;
            }
            long start = row.getLong(TAG_START);
            Entry entry = new Entry(ResourceKey.create(Registries.DIMENSION, id), row.getLong(TAG_HOME), start,
                BlockPos.of(row.getLong(TAG_CENTRE)));
            entry.claimed = row.getBoolean(TAG_CLAIMED);
            entry.moves = row.getInt(TAG_MOVES);
            entry.notBefore = row.getLong(TAG_NOT_BEFORE);
            if (row.contains(TAG_HELD_BACK, Tag.TAG_STRING)) {
                entry.heldBack = row.getString(TAG_HELD_BACK);
            }
            state.byDimension.computeIfAbsent(entry.dimension, key -> new HashMap<>()).put(start, entry);
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag pillars = new ListTag();
        for (Entry entry : entries()) {
            CompoundTag row = new CompoundTag();
            row.putString(TAG_DIMENSION, entry.dimension.location().toString());
            row.putLong(TAG_START, entry.start);
            row.putLong(TAG_HOME, entry.home);
            row.putLong(TAG_CENTRE, entry.centre.asLong());
            row.putBoolean(TAG_CLAIMED, entry.claimed);
            row.putInt(TAG_MOVES, entry.moves);
            row.putLong(TAG_NOT_BEFORE, entry.notBefore);
            if (entry.heldBack != null) {
                row.putString(TAG_HELD_BACK, entry.heldBack);
            }
            pillars.add(row);
        }
        tag.put(TAG_PILLARS, pillars);
        return tag;
    }
}
