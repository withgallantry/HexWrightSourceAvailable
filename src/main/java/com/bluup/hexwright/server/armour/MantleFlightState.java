package com.bluup.hexwright.server.armour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MantleFlightState extends SavedData {

    private static final String STORAGE_ID = "hexwright_mantle_flight";

    private final Set<UUID> granted = new HashSet<>();

    public static MantleFlightState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(MantleFlightState::load, MantleFlightState::new, STORAGE_ID);
    }

    public boolean granted(UUID player) {
        return granted.contains(player);
    }

    public boolean setGranted(UUID player, boolean value) {
        boolean changed = value ? granted.add(player) : granted.remove(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID player : granted) {
            list.add(StringTag.valueOf(player.toString()));
        }
        tag.put("Granted", list);
        return tag;
    }

    public static MantleFlightState load(CompoundTag tag) {
        MantleFlightState state = new MantleFlightState();
        for (Tag entry : tag.getList("Granted", Tag.TAG_STRING)) {
            try {
                state.granted.add(UUID.fromString(entry.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }
}
