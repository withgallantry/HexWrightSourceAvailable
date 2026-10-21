package com.bluup.hexwright.server.inspiration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InspirationState extends SavedData {

    private static final String STORAGE_ID = "hexwright_inspiration";

    private final Map<UUID, Integer> totals = new HashMap<>();

    public static InspirationState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(InspirationState::load, InspirationState::new, STORAGE_ID);
    }

    public int total(UUID player) {
        return totals.getOrDefault(player, 0);
    }

    public int add(UUID player, int amount) {
        int total = Math.max(0, total(player) + amount);
        set(player, total);
        return total;
    }

    public void set(UUID player, int total) {
        totals.put(player, Math.max(0, total));
        setDirty();
    }

    public Map<UUID, Integer> all() {
        return Map.copyOf(totals);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : totals.entrySet()) {
            all.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("Players", all);
        return tag;
    }

    public static InspirationState load(CompoundTag tag) {
        InspirationState state = new InspirationState();
        CompoundTag all = tag.getCompound("Players");
        for (String key : all.getAllKeys()) {
            try {
                state.totals.put(UUID.fromString(key), all.getInt(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }
}
