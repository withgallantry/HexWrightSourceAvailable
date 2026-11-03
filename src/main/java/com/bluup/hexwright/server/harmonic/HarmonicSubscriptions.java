package com.bluup.hexwright.server.harmonic;

import com.bluup.hexwright.server.network.ResonantRingItem;
import com.bluup.hexwright.server.network.ResonantRingNetwork;
import com.bluup.hexwright.server.network.RingSubscriptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HarmonicSubscriptions {

    public record Subscriber(UUID player, int ringIndex, String networkKey, int harmonic) {
    }

    private static final Map<String, Map<Integer, LinkedHashSet<Subscriber>>> BY_NETWORK = new HashMap<>();
    private static final Map<UUID, List<Subscriber>> BY_PLAYER = new HashMap<>();

    private HarmonicSubscriptions() {
    }


    public static List<Subscriber> subscribers(String networkKey, int harmonic) {
        Map<Integer, LinkedHashSet<Subscriber>> byHarmonic = BY_NETWORK.get(networkKey);
        if (byHarmonic == null) {
            return List.of();
        }
        LinkedHashSet<Subscriber> listeners = byHarmonic.get(harmonic);
        return listeners == null || listeners.isEmpty() ? List.of() : List.copyOf(listeners);
    }

    public static int[] countsByHarmonic(String networkKey) {
        int[] counts = new int[HarmonicExchangeState.HARMONIC_COUNT];
        Map<Integer, LinkedHashSet<Subscriber>> byHarmonic = BY_NETWORK.get(networkKey);
        if (byHarmonic == null) {
            return counts;
        }
        for (Map.Entry<Integer, LinkedHashSet<Subscriber>> entry : byHarmonic.entrySet()) {
            int harmonic = entry.getKey();
            if (HarmonicExchangeState.isValidHarmonic(harmonic)) {
                counts[harmonic] = entry.getValue().size();
            }
        }
        return counts;
    }

    public static int totalSubscribers(String networkKey) {
        int total = 0;
        for (int count : countsByHarmonic(networkKey)) {
            total += count;
        }
        return total;
    }


    public static void refresh(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID id = player.getUUID();
        List<Subscriber> desired = compute(player, server);
        List<Subscriber> current = BY_PLAYER.getOrDefault(id, List.of());
        if (desired.equals(current)) {
            return;
        }

        for (Subscriber subscriber : current) {
            detach(subscriber);
        }
        for (Subscriber subscriber : desired) {
            attach(subscriber);
        }
        if (desired.isEmpty()) {
            BY_PLAYER.remove(id);
        } else {
            BY_PLAYER.put(id, desired);
        }

        for (Subscriber subscriber : desired) {
            if (!current.contains(subscriber)) {
                deliverRetained(server, subscriber);
            }
        }

        for (String networkKey : touchedNetworks(current, desired)) {
            HarmonicNetwork.notifyExchange(server, networkKey);
        }
    }

    public static void unregister(MinecraftServer server, UUID id) {
        List<Subscriber> current = BY_PLAYER.remove(id);
        if (current == null || current.isEmpty()) {
            return;
        }
        for (Subscriber subscriber : current) {
            detach(subscriber);
        }
        for (String networkKey : touchedNetworks(current, List.of())) {
            HarmonicNetwork.notifyExchange(server, networkKey);
        }
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    public static void clearAll() {
        BY_NETWORK.clear();
        BY_PLAYER.clear();
    }

    private static List<Subscriber> compute(ServerPlayer player, MinecraftServer server) {
        List<ItemStack> rings = ResonantRingNetwork.worn(player);
        if (rings.isEmpty()) {
            return List.of();
        }
        List<Subscriber> out = new ArrayList<>();
        for (int index = 0; index < rings.size(); index++) {
            ItemStack ring = rings.get(index);
            String networkKey = ResonantRingItem.networkKey(ring);
            if (networkKey == null || !HarmonicNetwork.isActive(server, networkKey)) {
                continue;
            }
            for (RingSubscriptions.Subscription subscription : RingSubscriptions.list(ring)) {
                if (HarmonicExchangeState.isValidHarmonic(subscription.harmonic())) {
                    out.add(new Subscriber(player.getUUID(), index, networkKey, subscription.harmonic()));
                }
            }
        }
        return out;
    }

    private static void attach(Subscriber subscriber) {
        BY_NETWORK
            .computeIfAbsent(subscriber.networkKey(), key -> new HashMap<>())
            .computeIfAbsent(subscriber.harmonic(), key -> new LinkedHashSet<>())
            .add(subscriber);
    }

    private static void detach(Subscriber subscriber) {
        Map<Integer, LinkedHashSet<Subscriber>> byHarmonic = BY_NETWORK.get(subscriber.networkKey());
        if (byHarmonic == null) {
            return;
        }
        LinkedHashSet<Subscriber> listeners = byHarmonic.get(subscriber.harmonic());
        if (listeners == null) {
            return;
        }
        listeners.remove(subscriber);
        if (listeners.isEmpty()) {
            byHarmonic.remove(subscriber.harmonic());
        }
        if (byHarmonic.isEmpty()) {
            BY_NETWORK.remove(subscriber.networkKey());
        }
    }

    private static void deliverRetained(MinecraftServer server, Subscriber subscriber) {
        net.minecraft.nbt.CompoundTag retained = HarmonicExchangeState.get(server)
            .retainedTag(subscriber.networkKey(), subscriber.harmonic());
        if (retained != null) {
            HarmonicDelivery.enqueueSerialised(new HarmonicDelivery.Ring(subscriber), retained);
        }
    }

    private static List<String> touchedNetworks(List<Subscriber> before, List<Subscriber> after) {
        LinkedHashSet<String> networks = new LinkedHashSet<>();
        for (Subscriber subscriber : before) {
            networks.add(subscriber.networkKey());
        }
        for (Subscriber subscriber : after) {
            networks.add(subscriber.networkKey());
        }
        return List.copyOf(networks);
    }
}
