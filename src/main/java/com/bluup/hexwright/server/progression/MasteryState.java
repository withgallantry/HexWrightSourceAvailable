package com.bluup.hexwright.server.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MasteryState extends SavedData {

    private static final String STORAGE_ID = "hexwright_mastery";

    private final Map<UUID, PlayerMastery> players = new HashMap<>();

    public static MasteryState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(MasteryState::load, MasteryState::new, STORAGE_ID);
    }

    public PlayerMastery mastery(UUID player) {
        return players.computeIfAbsent(player, id -> new PlayerMastery());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, PlayerMastery> entry : players.entrySet()) {
            all.put(entry.getKey().toString(), entry.getValue().save());
        }
        tag.put("Players", all);
        return tag;
    }

    public static MasteryState load(CompoundTag tag) {
        MasteryState state = new MasteryState();
        CompoundTag all = tag.getCompound("Players");
        for (String key : all.getAllKeys()) {
            try {
                state.players.put(UUID.fromString(key), PlayerMastery.load(all.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }
}
