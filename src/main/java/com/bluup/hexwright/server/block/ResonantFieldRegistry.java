package com.bluup.hexwright.server.block;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResonantFieldRegistry extends SavedData {
    private static final String STORAGE_ID = "hexwright_resonant_fields";

    public record Field(UUID id, ResourceKey<Level> dimension, AABB bounds, List<BlockPos> markers) {
        public boolean containsBoth(Vec3 a, Vec3 b) {
            return bounds.contains(a) && bounds.contains(b);
        }
    }

    private final Map<UUID, Field> fields = new HashMap<>();

    public static ResonantFieldRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(ResonantFieldRegistry::load, ResonantFieldRegistry::new, STORAGE_ID);
    }

    public Field create(ResourceKey<Level> dimension, List<BlockPos> markers) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : markers) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        Field field = new Field(UUID.randomUUID(), dimension, bounds, List.copyOf(markers));
        fields.put(field.id(), field);
        setDirty();
        return field;
    }

    public void remove(UUID id) {
        if (fields.remove(id) != null) {
            setDirty();
        }
    }

    public Field get(UUID id) {
        return fields.get(id);
    }

    public List<Field> fieldsIn(ResourceKey<Level> dimension) {
        List<Field> result = new ArrayList<>();
        for (Field field : fields.values()) {
            if (field.dimension().equals(dimension)) {
                result.add(field);
            }
        }
        return result;
    }

    private ResonantFieldRegistry() {
    }

    private static ResonantFieldRegistry load(CompoundTag tag) {
        ResonantFieldRegistry registry = new ResonantFieldRegistry();
        ListTag entries = tag.getList("Fields", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = entry.getUUID("Id");
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(entry.getString("Dim")));
            AABB bounds = new AABB(
                entry.getDouble("MinX"), entry.getDouble("MinY"), entry.getDouble("MinZ"),
                entry.getDouble("MaxX"), entry.getDouble("MaxY"), entry.getDouble("MaxZ")
            );
            ListTag markerList = entry.getList("Markers", Tag.TAG_COMPOUND);
            List<BlockPos> markers = new ArrayList<>();
            for (int j = 0; j < markerList.size(); j++) {
                CompoundTag posTag = markerList.getCompound(j);
                markers.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
            }
            registry.fields.put(id, new Field(id, dimension, bounds, markers));
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Field field : fields.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", field.id());
            entry.putString("Dim", field.dimension().location().toString());
            AABB bounds = field.bounds();
            entry.putDouble("MinX", bounds.minX);
            entry.putDouble("MinY", bounds.minY);
            entry.putDouble("MinZ", bounds.minZ);
            entry.putDouble("MaxX", bounds.maxX);
            entry.putDouble("MaxY", bounds.maxY);
            entry.putDouble("MaxZ", bounds.maxZ);
            ListTag markerList = new ListTag();
            for (BlockPos pos : field.markers()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", pos.getX());
                posTag.putInt("Y", pos.getY());
                posTag.putInt("Z", pos.getZ());
                markerList.add(posTag);
            }
            entry.put("Markers", markerList);
            entries.add(entry);
        }
        tag.put("Fields", entries);
        return tag;
    }
}
