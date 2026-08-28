package com.bluup.hexwright.server.harmonic;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import com.bluup.hexwright.server.network.ResonantAttunement;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class HarmonicExchangeState extends SavedData {

    private static final String STORAGE_ID = "hexwright_harmonic_exchanges";

    public static final int HARMONIC_COUNT = 16;

    public static final class Exchange {
        private ResourceKey<Level> dimension;
        private BlockPos pos;

        private final Map<Integer, CompoundTag> retained = new LinkedHashMap<>();

        private final TransmissionWindow transmissions = new TransmissionWindow();

        private Exchange(ResourceKey<Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public BlockPos pos() {
            return pos;
        }
    }

    private final Map<String, Exchange> byNetwork = new HashMap<>();

    private final Map<String, String> networkByLocation = new HashMap<>();

    public static HarmonicExchangeState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(HarmonicExchangeState::load, HarmonicExchangeState::new, STORAGE_ID);
    }

    public static boolean isValidHarmonic(int harmonic) {
        return harmonic >= 0 && harmonic < HARMONIC_COUNT;
    }

    private static String locationKey(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "@" + pos.asLong();
    }

    public static boolean anchorsInRange(String networkKey, ResourceKey<Level> dimension, BlockPos pos) {
        return ResonantAttunement.withinTowerRange(networkKey, dimension.location().toString(), pos);
    }

    public boolean register(String networkKey, ResourceKey<Level> dimension, BlockPos pos) {
        String location = locationKey(dimension, pos);
        String previous = networkByLocation.get(location);
        if (networkKey.equals(previous)) {
            return false;
        }
        if (previous != null) {
            dropNetwork(previous);
        }

        Exchange existing = byNetwork.get(networkKey);
        if (existing != null) {
            networkByLocation.remove(locationKey(existing.dimension, existing.pos));
            existing.dimension = dimension;
            existing.pos = pos;
        } else {
            byNetwork.put(networkKey, new Exchange(dimension, pos));
        }
        networkByLocation.put(location, networkKey);
        setDirty();
        return true;
    }

    public @Nullable String unregisterAt(ResourceKey<Level> dimension, BlockPos pos) {
        String networkKey = networkByLocation.remove(locationKey(dimension, pos));
        if (networkKey == null) {
            return null;
        }
        byNetwork.remove(networkKey);
        setDirty();
        return networkKey;
    }

    private void dropNetwork(String networkKey) {
        Exchange exchange = byNetwork.remove(networkKey);
        if (exchange != null) {
            networkByLocation.remove(locationKey(exchange.dimension, exchange.pos));
        }
        setDirty();
    }

    public @Nullable Exchange exchangeFor(String networkKey) {
        return byNetwork.get(networkKey);
    }

    public @Nullable String networkAt(ResourceKey<Level> dimension, BlockPos pos) {
        return networkByLocation.get(locationKey(dimension, pos));
    }


    public void setRetained(String networkKey, int harmonic, Iota state) {
        Exchange exchange = byNetwork.get(networkKey);
        if (exchange == null || !isValidHarmonic(harmonic)) {
            return;
        }
        exchange.retained.put(harmonic, IotaType.serialize(state));
        setDirty();
    }

    public @Nullable Iota retained(String networkKey, int harmonic, ServerLevel level) {
        CompoundTag tag = retainedTag(networkKey, harmonic);
        return tag == null ? null : IotaType.deserialize(tag.copy(), level);
    }

    public @Nullable CompoundTag retainedTag(String networkKey, int harmonic) {
        Exchange exchange = byNetwork.get(networkKey);
        return exchange == null ? null : exchange.retained.get(harmonic);
    }

    public boolean hasRetained(String networkKey, int harmonic) {
        Exchange exchange = byNetwork.get(networkKey);
        return exchange != null && exchange.retained.containsKey(harmonic);
    }


    public void recordTransmission(String networkKey, long gameTime) {
        Exchange exchange = byNetwork.get(networkKey);
        if (exchange == null) {
            return;
        }
        exchange.transmissions.record(gameTime);
        setDirty();
    }

    public int transmissionsLastMinute(String networkKey, long gameTime) {
        Exchange exchange = byNetwork.get(networkKey);
        return exchange == null ? 0 : exchange.transmissions.lastMinute(gameTime);
    }

    private HarmonicExchangeState() {
    }

    private static HarmonicExchangeState load(CompoundTag tag) {
        HarmonicExchangeState state = new HarmonicExchangeState();
        ListTag entries = tag.getList("Exchanges", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String networkKey = entry.getString("Network");
            if (networkKey.isEmpty()) {
                continue;
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(entry.getString("Dim")));
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            if (!anchorsInRange(networkKey, dimension, pos)) {
                continue;
            }
            Exchange exchange = new Exchange(dimension, pos);

            ListTag retained = entry.getList("Retained", Tag.TAG_COMPOUND);
            for (int j = 0; j < retained.size(); j++) {
                CompoundTag slot = retained.getCompound(j);
                int harmonic = slot.getInt("Harmonic");
                if (isValidHarmonic(harmonic)) {
                    exchange.retained.put(harmonic, slot.getCompound("Iota"));
                }
            }

            state.byNetwork.put(networkKey, exchange);
            state.networkByLocation.put(locationKey(dimension, pos), networkKey);
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<String, Exchange> entry : byNetwork.entrySet()) {
            Exchange exchange = entry.getValue();
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Network", entry.getKey());
            entryTag.putString("Dim", exchange.dimension.location().toString());
            entryTag.putLong("Pos", exchange.pos.asLong());

            ListTag retained = new ListTag();
            for (Map.Entry<Integer, CompoundTag> slot : exchange.retained.entrySet()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("Harmonic", slot.getKey());
                slotTag.put("Iota", slot.getValue());
                retained.add(slotTag);
            }
            entryTag.put("Retained", retained);
            entries.add(entryTag);
        }
        tag.put("Exchanges", entries);
        return tag;
    }
}
