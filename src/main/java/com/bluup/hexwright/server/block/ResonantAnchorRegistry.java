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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResonantAnchorRegistry extends SavedData {
    private static final String STORAGE_ID = "hexwright_resonant_anchors";

    public record AnchorLocation(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private final Map<String, Set<AnchorLocation>> byKey = new HashMap<>();

    public static ResonantAnchorRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(ResonantAnchorRegistry::load, ResonantAnchorRegistry::new, STORAGE_ID);
    }

    public void register(String key, ResourceKey<Level> dimension, BlockPos pos) {
        byKey.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(new AnchorLocation(dimension, pos));
        setDirty();
    }

    public void unregister(String key, ResourceKey<Level> dimension, BlockPos pos) {
        Set<AnchorLocation> anchors = byKey.get(key);
        if (anchors == null) {
            return;
        }
        if (anchors.remove(new AnchorLocation(dimension, pos))) {
            if (anchors.isEmpty()) {
                byKey.remove(key);
            }
            setDirty();
        }
    }

    public List<AnchorLocation> anchorsFor(String key) {
        Set<AnchorLocation> anchors = byKey.get(key);
        return anchors == null ? List.of() : List.copyOf(anchors);
    }

    private ResonantAnchorRegistry() {
    }

    private static ResonantAnchorRegistry load(CompoundTag tag) {
        ResonantAnchorRegistry registry = new ResonantAnchorRegistry();
        ListTag entries = tag.getList("Keys", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String key = entry.getString("Key");
            Set<AnchorLocation> anchors = new LinkedHashSet<>();
            ListTag posList = entry.getList("Anchors", Tag.TAG_COMPOUND);
            for (int j = 0; j < posList.size(); j++) {
                CompoundTag posTag = posList.getCompound(j);
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    new ResourceLocation(posTag.getString("Dim")));
                BlockPos pos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                anchors.add(new AnchorLocation(dimension, pos));
            }
            if (!anchors.isEmpty()) {
                registry.byKey.put(key, anchors);
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<String, Set<AnchorLocation>> entry : byKey.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Key", entry.getKey());
            ListTag posList = new ListTag();
            for (AnchorLocation anchor : entry.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putString("Dim", anchor.dimension().location().toString());
                posTag.putInt("X", anchor.pos().getX());
                posTag.putInt("Y", anchor.pos().getY());
                posTag.putInt("Z", anchor.pos().getZ());
                posList.add(posTag);
            }
            entryTag.put("Anchors", posList);
            entries.add(entryTag);
        }
        tag.put("Keys", entries);
        return tag;
    }
}
