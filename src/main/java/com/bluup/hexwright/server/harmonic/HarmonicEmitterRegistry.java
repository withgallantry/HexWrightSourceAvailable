package com.bluup.hexwright.server.harmonic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class HarmonicEmitterRegistry {

    public record Address(ResourceKey<Level> dimension, BlockPos pos, String networkKey, int harmonic) {
    }

    private static final Map<String, Map<Integer, LinkedHashSet<Address>>> BY_NETWORK = new HashMap<>();

    private static final Map<Location, Address> BY_LOCATION = new HashMap<>();

    private record Location(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private HarmonicEmitterRegistry() {
    }

    public static List<Address> emitters(String networkKey, int harmonic) {
        Map<Integer, LinkedHashSet<Address>> byHarmonic = BY_NETWORK.get(networkKey);
        if (byHarmonic == null) {
            return List.of();
        }
        LinkedHashSet<Address> listeners = byHarmonic.get(harmonic);
        return listeners == null || listeners.isEmpty() ? List.of() : List.copyOf(listeners);
    }

    public static int[] countsByHarmonic(String networkKey) {
        int[] counts = new int[HarmonicExchangeState.HARMONIC_COUNT];
        Map<Integer, LinkedHashSet<Address>> byHarmonic = BY_NETWORK.get(networkKey);
        if (byHarmonic == null) {
            return counts;
        }
        for (Map.Entry<Integer, LinkedHashSet<Address>> entry : byHarmonic.entrySet()) {
            if (HarmonicExchangeState.isValidHarmonic(entry.getKey())) {
                counts[entry.getKey()] = entry.getValue().size();
            }
        }
        return counts;
    }

    public static void register(ResourceKey<Level> dimension, BlockPos pos, String networkKey, int harmonic) {
        Location location = new Location(dimension, pos.immutable());
        Address desired = new Address(dimension, location.pos(), networkKey, harmonic);
        Address current = BY_LOCATION.get(location);
        if (desired.equals(current)) {
            return;
        }
        if (current != null) {
            detach(current);
        }
        BY_LOCATION.put(location, desired);
        BY_NETWORK
            .computeIfAbsent(networkKey, key -> new HashMap<>())
            .computeIfAbsent(harmonic, key -> new LinkedHashSet<>())
            .add(desired);
    }

    public static void unregister(ResourceKey<Level> dimension, BlockPos pos) {
        Address current = BY_LOCATION.remove(new Location(dimension, pos.immutable()));
        if (current != null) {
            detach(current);
        }
    }

    public static void clearAll() {
        BY_NETWORK.clear();
        BY_LOCATION.clear();
    }

    private static void detach(Address address) {
        Map<Integer, LinkedHashSet<Address>> byHarmonic = BY_NETWORK.get(address.networkKey());
        if (byHarmonic == null) {
            return;
        }
        LinkedHashSet<Address> listeners = byHarmonic.get(address.harmonic());
        if (listeners == null) {
            return;
        }
        listeners.remove(address);
        if (listeners.isEmpty()) {
            byHarmonic.remove(address.harmonic());
        }
        if (byHarmonic.isEmpty()) {
            BY_NETWORK.remove(address.networkKey());
        }
    }
}
