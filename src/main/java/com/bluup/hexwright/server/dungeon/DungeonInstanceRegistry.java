package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class DungeonInstanceRegistry extends SavedData {

    private static final String STORAGE_ID = "hexwright_dungeon_instances";

    private final Map<Integer, DungeonInstance> instances = new TreeMap<>();
    private final Map<UUID, DungeonInstance.Entrance> stranded = new HashMap<>();
    private int nextId;

    private DungeonInstanceRegistry() {
    }

    public static DungeonInstanceRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(DungeonInstanceRegistry::load, DungeonInstanceRegistry::new, STORAGE_ID);
    }

    public List<DungeonInstance> all() {
        return List.copyOf(this.instances.values());
    }

    public int size() {
        return this.instances.size();
    }

    public @Nullable DungeonInstance byId(int id) {
        return this.instances.get(id);
    }

    public @Nullable DungeonInstance byCell(int cell) {
        for (DungeonInstance instance : this.instances.values()) {
            if (instance.cell() == cell) {
                return instance;
            }
        }
        return null;
    }

    int freeCell(int cells) {
        for (int cell = 0; cell < cells; cell++) {
            if (byCell(cell) == null) {
                return cell;
            }
        }
        return -1;
    }

    DungeonInstance create(DungeonInstance.Kind kind, int cell, long seed, UUID opener,
                           DungeonInstance.Entrance entrance, BoundingBox extent, long startChunk, BlockPos shaftFoot,
                           BlockPos safeRoomAt) {
        DungeonInstance instance = new DungeonInstance(this.nextId++, kind, cell, seed, opener, entrance, extent,
            startChunk, shaftFoot, safeRoomAt, DungeonInstance.State.BUILDING);
        this.instances.put(instance.id(), instance);
        setDirty();
        return instance;
    }

    void remove(DungeonInstance instance) {
        if (this.instances.remove(instance.id()) != null) {
            setDirty();
        }
    }

    void strand(UUID player, DungeonInstance.Entrance entrance) {
        this.stranded.put(player, entrance);
        setDirty();
    }

    @Nullable DungeonInstance.Entrance takeStranded(UUID player) {
        DungeonInstance.Entrance entrance = this.stranded.remove(player);
        if (entrance != null) {
            setDirty();
        }
        return entrance;
    }

    private static DungeonInstanceRegistry load(CompoundTag tag) {
        DungeonInstanceRegistry registry = new DungeonInstanceRegistry();
        registry.nextId = tag.getInt("NextId");
        ListTag instances = tag.getList("Instances", Tag.TAG_COMPOUND);
        for (int index = 0; index < instances.size(); index++) {
            DungeonInstance instance = DungeonInstance.load(instances.getCompound(index));
            if (instance == null) {
                Hexwright.LOGGER.error("Dropping unreadable dungeon instance record {}", instances.getCompound(index));
                continue;
            }
            registry.instances.put(instance.id(), instance);
            registry.nextId = Math.max(registry.nextId, instance.id() + 1);
        }
        ListTag stranded = tag.getList("Stranded", Tag.TAG_COMPOUND);
        for (int index = 0; index < stranded.size(); index++) {
            CompoundTag row = stranded.getCompound(index);
            DungeonInstance.Entrance entrance = DungeonInstance.Entrance.load(row);
            if (row.hasUUID("Player") && entrance != null) {
                registry.stranded.put(row.getUUID("Player"), entrance);
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextId", this.nextId);
        ListTag instances = new ListTag();
        for (DungeonInstance instance : this.instances.values()) {
            instances.add(instance.save());
        }
        tag.put("Instances", instances);
        ListTag stranded = new ListTag();
        this.stranded.forEach((player, entrance) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("Player", player);
            entrance.save(row);
            stranded.add(row);
        });
        tag.put("Stranded", stranded);
        return tag;
    }
}
