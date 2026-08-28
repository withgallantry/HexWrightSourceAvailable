package com.bluup.hexwright.server.worldgen.arena;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GolemClears extends SavedData {

    private static final String STORAGE_ID = "hexwright_golem_clears";
    private static final String TAG_ARENAS = "Arenas";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_STARTS = "Starts";

    private final Map<ResourceKey<Level>, Set<Long>> byDimension = new HashMap<>();

    public static GolemClears get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(GolemClears::load, GolemClears::new, STORAGE_ID);
    }

    public boolean isCleared(ResourceKey<Level> dimension, long startChunk) {
        Set<Long> starts = this.byDimension.get(dimension);
        return starts != null && starts.contains(startChunk);
    }

    public boolean clear(ResourceKey<Level> dimension, long startChunk) {
        if (!this.byDimension.computeIfAbsent(dimension, key -> new HashSet<>()).add(startChunk)) {
            return false;
        }
        setDirty();
        return true;
    }

    private static GolemClears load(CompoundTag tag) {
        GolemClears state = new GolemClears();
        ListTag arenas = tag.getList(TAG_ARENAS, Tag.TAG_COMPOUND);
        for (int index = 0; index < arenas.size(); index++) {
            CompoundTag row = arenas.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(row.getString(TAG_DIMENSION));
            if (id == null) {
                continue;
            }
            Set<Long> starts = new HashSet<>();
            for (long start : row.getLongArray(TAG_STARTS)) {
                starts.add(start);
            }
            if (!starts.isEmpty()) {
                state.byDimension.put(ResourceKey.create(Registries.DIMENSION, id), starts);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag arenas = new ListTag();
        this.byDimension.forEach((dimension, starts) -> {
            CompoundTag row = new CompoundTag();
            row.putString(TAG_DIMENSION, dimension.location().toString());
            long[] packed = new long[starts.size()];
            int index = 0;
            for (long start : starts) {
                packed[index++] = start;
            }
            row.putLongArray(TAG_STARTS, packed);
            arenas.add(row);
        });
        tag.put(TAG_ARENAS, arenas);
        return tag;
    }
}
