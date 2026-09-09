package com.bluup.hexwright.server.journal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InvestigationState extends SavedData {

    private static final String STORAGE_ID = "hexwright_investigations";

    private static final String COMPLETED_KEY = "Completed";
    private static final String COUNTERS_KEY = "Counters";
    private static final String ISSUED_KEY = "Issued";
    private static final String PLAYERS_KEY = "Players";

    private final Map<UUID, Set<String>> completed = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> counters = new HashMap<>();
    private final Set<UUID> issued = new HashSet<>();

    public static InvestigationState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(InvestigationState::load, InvestigationState::new, STORAGE_ID);
    }

    public Set<String> completed(UUID player) {
        return Collections.unmodifiableSet(completed.computeIfAbsent(player, id -> new HashSet<>()));
    }

    public boolean isComplete(UUID player, String investigationId) {
        Set<String> theirs = completed.get(player);
        return theirs != null && theirs.contains(investigationId);
    }

    public boolean complete(UUID player, String investigationId) {
        if (!completed.computeIfAbsent(player, id -> new HashSet<>()).add(investigationId)) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean uncomplete(UUID player, String investigationId) {
        Set<String> theirs = completed.get(player);
        if (theirs == null || !theirs.remove(investigationId)) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean uncompleteAll(UUID player) {
        Set<String> theirs = completed.get(player);
        if (theirs == null || theirs.isEmpty()) {
            return false;
        }
        theirs.clear();
        setDirty();
        return true;
    }

    public boolean claimStarterJournal(UUID player) {
        if (!issued.add(player)) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean forgetStarterJournal(UUID player) {
        if (!issued.remove(player)) {
            return false;
        }
        setDirty();
        return true;
    }

    public int counter(UUID player, String key) {
        Map<String, Integer> theirs = counters.get(player);
        return theirs == null ? 0 : theirs.getOrDefault(key, 0);
    }

    public int addToCounter(UUID player, String key, int amount) {
        Map<String, Integer> theirs = counters.computeIfAbsent(player, id -> new HashMap<>());
        int updated = (int) Math.min(Integer.MAX_VALUE, (long) theirs.getOrDefault(key, 0) + amount);
        theirs.put(key, updated);
        setDirty();
        return updated;
    }

    public boolean clearCounters(UUID player) {
        Map<String, Integer> theirs = counters.get(player);
        if (theirs == null || theirs.isEmpty()) {
            return false;
        }
        theirs.clear();
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (UUID player : union()) {
            CompoundTag entry = new CompoundTag();

            ListTag done = new ListTag();
            for (String id : completed.getOrDefault(player, Set.of())) {
                done.add(StringTag.valueOf(id));
            }
            entry.put(COMPLETED_KEY, done);

            CompoundTag tallies = new CompoundTag();
            for (Map.Entry<String, Integer> counter : counters.getOrDefault(player, Map.of()).entrySet()) {
                tallies.putInt(counter.getKey(), counter.getValue());
            }
            entry.put(COUNTERS_KEY, tallies);

            if (issued.contains(player)) {
                entry.putBoolean(ISSUED_KEY, true);
            }

            players.put(player.toString(), entry);
        }
        tag.put(PLAYERS_KEY, players);
        return tag;
    }

    public static InvestigationState load(CompoundTag tag) {
        InvestigationState state = new InvestigationState();
        CompoundTag players = tag.getCompound(PLAYERS_KEY);
        for (String key : players.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            CompoundTag entry = players.getCompound(key);

            Set<String> done = state.completed.computeIfAbsent(player, id -> new HashSet<>());
            ListTag list = entry.getList(COMPLETED_KEY, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                done.add(list.getString(i));
            }

            if (entry.getBoolean(ISSUED_KEY)) {
                state.issued.add(player);
            }

            CompoundTag tallies = entry.getCompound(COUNTERS_KEY);
            if (!tallies.isEmpty()) {
                Map<String, Integer> theirs = state.counters.computeIfAbsent(player, id -> new HashMap<>());
                for (String counter : tallies.getAllKeys()) {
                    theirs.put(counter, tallies.getInt(counter));
                }
            }
        }
        return state;
    }

    private Set<UUID> union() {
        Set<UUID> all = new HashSet<>(completed.keySet());
        all.addAll(counters.keySet());
        all.addAll(issued);
        return all;
    }
}
