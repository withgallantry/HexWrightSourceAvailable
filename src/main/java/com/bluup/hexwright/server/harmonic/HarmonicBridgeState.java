package com.bluup.hexwright.server.harmonic;

import com.bluup.hexwright.server.block.ExchangeBridgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HarmonicBridgeState extends SavedData {

    private static final String STORAGE_ID = "hexwright_exchange_bridges";

    public record Bridge(
        ResourceKey<Level> dimension,
        BlockPos pos,
        String networkA,
        String networkB,
        int harmonic
    ) {
        public @Nullable String across(String from) {
            if (networkA.equals(from)) {
                return networkB;
            }
            if (networkB.equals(from)) {
                return networkA;
            }
            return null;
        }
    }

    private final Map<String, Bridge> byLocation = new LinkedHashMap<>();

    private final Map<String, Set<String>> locationsByNetwork = new HashMap<>();

    public static HarmonicBridgeState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(HarmonicBridgeState::load, HarmonicBridgeState::new, STORAGE_ID);
    }

    private static String locationKey(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "@" + pos.asLong();
    }

    public boolean register(ResourceKey<Level> dimension, BlockPos pos,
                            String networkA, String networkB, int harmonic) {
        Bridge desired = new Bridge(dimension, pos.immutable(), networkA, networkB, harmonic);
        String location = locationKey(dimension, desired.pos());
        if (desired.equals(byLocation.get(location))) {
            return false;
        }
        detach(location);
        byLocation.put(location, desired);
        attach(location, desired);
        setDirty();
        return true;
    }

    public boolean unregisterAt(ResourceKey<Level> dimension, BlockPos pos) {
        if (detach(locationKey(dimension, pos.immutable()))) {
            setDirty();
            return true;
        }
        return false;
    }

    private void attach(String location, Bridge bridge) {
        locationsByNetwork.computeIfAbsent(bridge.networkA(), key -> new HashSet<>()).add(location);
        locationsByNetwork.computeIfAbsent(bridge.networkB(), key -> new HashSet<>()).add(location);
    }

    private boolean detach(String location) {
        Bridge previous = byLocation.remove(location);
        if (previous == null) {
            return false;
        }
        for (String network : List.of(previous.networkA(), previous.networkB())) {
            Set<String> locations = locationsByNetwork.get(network);
            if (locations != null && locations.remove(location) && locations.isEmpty()) {
                locationsByNetwork.remove(network);
            }
        }
        return true;
    }


    public List<Bridge> bridgesFrom(MinecraftServer server, String from, int harmonic) {
        Set<String> locations = locationsByNetwork.get(from);
        if (locations == null || locations.isEmpty()) {
            return List.of();
        }
        List<Bridge> found = new ArrayList<>(2);
        List<String> stale = null;
        for (String location : List.copyOf(locations)) {
            Bridge bridge = byLocation.get(location);
            if (bridge == null || bridge.harmonic() != harmonic) {
                continue;
            }
            String far = bridge.across(from);
            if (far == null || far.equals(from)) {
                continue;
            }
            if (!verify(server, bridge)) {
                if (stale == null) {
                    stale = new ArrayList<>(1);
                }
                stale.add(location);
                continue;
            }
            found.add(bridge);
        }
        if (stale != null) {
            for (String location : stale) {
                detach(location);
            }
            setDirty();
        }
        return found;
    }

    public void refreshBridgesOn(MinecraftServer server, String networkKey) {
        Set<String> locations = locationsByNetwork.get(networkKey);
        if (locations == null || locations.isEmpty()) {
            return;
        }
        for (String location : List.copyOf(locations)) {
            Bridge bridge = byLocation.get(location);
            if (bridge == null) {
                continue;
            }
            ServerLevel level = server.getLevel(bridge.dimension());
            if (level == null || !level.isLoaded(bridge.pos())) {
                continue;
            }
            if (level.getBlockEntity(bridge.pos()) instanceof ExchangeBridgeBlockEntity block) {
                block.onNetworkActivity();
            }
        }
    }

    private static boolean verify(MinecraftServer server, Bridge bridge) {
        ServerLevel level = server.getLevel(bridge.dimension());
        if (level == null || !level.isLoaded(bridge.pos())) {
            return true;
        }
        return level.getBlockEntity(bridge.pos()) instanceof ExchangeBridgeBlockEntity block
            && block.carries(bridge.networkA(), bridge.networkB(), bridge.harmonic());
    }

    private HarmonicBridgeState() {
    }

    private static HarmonicBridgeState load(CompoundTag tag) {
        HarmonicBridgeState state = new HarmonicBridgeState();
        ListTag entries = tag.getList("Bridges", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String networkA = entry.getString("NetworkA");
            String networkB = entry.getString("NetworkB");
            int harmonic = entry.getInt("Harmonic");
            if (networkA.isEmpty() || networkB.isEmpty() || networkA.equals(networkB)
                || !HarmonicExchangeState.isValidHarmonic(harmonic)) {
                continue;
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(entry.getString("Dim")));
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            Bridge bridge = new Bridge(dimension, pos, networkA, networkB, harmonic);
            String location = locationKey(dimension, pos);
            state.byLocation.put(location, bridge);
            state.attach(location, bridge);
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Bridge bridge : byLocation.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dim", bridge.dimension().location().toString());
            entry.putLong("Pos", bridge.pos().asLong());
            entry.putString("NetworkA", bridge.networkA());
            entry.putString("NetworkB", bridge.networkB());
            entry.putInt("Harmonic", bridge.harmonic());
            entries.add(entry);
        }
        tag.put("Bridges", entries);
        return tag;
    }
}
