package com.bluup.hexwright.server.harmonic;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import com.bluup.hexwright.server.block.ExchangeBridgeBlockEntity;
import com.bluup.hexwright.server.block.HarmonicExchangeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HarmonicNetwork {

    public static final int NONE_DELIVERED = 0;

    private HarmonicNetwork() {
    }


    public static boolean isActive(MinecraftServer server, String networkKey) {
        HarmonicExchangeState state = HarmonicExchangeState.get(server);
        HarmonicExchangeState.Exchange exchange = state.exchangeFor(networkKey);
        if (exchange == null) {
            return false;
        }
        ServerLevel level = server.getLevel(exchange.dimension());
        if (level != null && level.isLoaded(exchange.pos())) {
            if (!(level.getBlockEntity(exchange.pos()) instanceof HarmonicExchangeBlockEntity block)
                || !networkKey.equals(block.networkKey())) {
                state.unregisterAt(exchange.dimension(), exchange.pos());
                return false;
            }
        }
        return true;
    }


    public static int publish(MinecraftServer server, String networkKey, int harmonic, Iota payload) {
        return send(server, networkKey, harmonic, payload, false);
    }

    public static int setState(MinecraftServer server, String networkKey, int harmonic, Iota state) {
        return send(server, networkKey, harmonic, state, true);
    }

    private static final int MAX_BRIDGE_HOPS = 4;

    private static int send(MinecraftServer server, String networkKey, int harmonic, Iota payload, boolean retain) {
        HarmonicExchangeState state = HarmonicExchangeState.get(server);
        if (state.exchangeFor(networkKey) == null) {
            return NONE_DELIVERED;
        }

        int delivered = deliverOn(server, state, networkKey, harmonic, payload, retain);

        HarmonicBridgeState bridges = HarmonicBridgeState.get(server);
        Set<String> reached = new HashSet<>();
        reached.add(networkKey);
        List<String> frontier = List.of(networkKey);
        for (int hop = 1; hop <= MAX_BRIDGE_HOPS && !frontier.isEmpty(); hop++) {
            List<String> next = new ArrayList<>();
            for (String near : frontier) {
                for (HarmonicBridgeState.Bridge bridge : bridges.bridgesFrom(server, near, harmonic)) {
                    String far = bridge.across(near);
                    if (far == null || state.exchangeFor(far) == null) {
                        continue;
                    }
                    notifyBridge(server, bridge);
                    if (!reached.add(far)) {
                        continue;
                    }
                    delivered += deliverOn(server, state, far, harmonic, payload, retain);
                    next.add(far);
                }
            }
            frontier = next;
        }
        return delivered;
    }

    private static int deliverOn(MinecraftServer server, HarmonicExchangeState state,
                                 String networkKey, int harmonic, Iota payload, boolean retain) {
        if (retain) {
            state.setRetained(networkKey, harmonic, payload);
        }
        state.recordTransmission(networkKey, server.overworld().getGameTime());

        int delivered = 0;
        for (HarmonicSubscriptions.Subscriber subscriber : HarmonicSubscriptions.subscribers(networkKey, harmonic)) {
            if (HarmonicDelivery.enqueue(new HarmonicDelivery.Ring(subscriber), payload)) {
                delivered++;
            }
        }
        for (HarmonicEmitterRegistry.Address address : HarmonicEmitterRegistry.emitters(networkKey, harmonic)) {
            if (HarmonicDelivery.enqueue(new HarmonicDelivery.Emitter(address), payload)) {
                delivered++;
            }
        }
        notifyExchange(server, networkKey);
        return delivered;
    }

    public static boolean isPayloadTooLarge(Iota payload) {
        return IotaType.isTooLargeToSerialize(List.of(payload));
    }


    public static void notifyExchange(MinecraftServer server, String networkKey) {
        HarmonicExchangeState.Exchange exchange = HarmonicExchangeState.get(server).exchangeFor(networkKey);
        if (exchange == null) {
            return;
        }
        ResourceKey<Level> dimension = exchange.dimension();
        BlockPos pos = exchange.pos();
        ServerLevel level = server.getLevel(dimension);
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof HarmonicExchangeBlockEntity block) {
            block.onNetworkActivity();
        }
    }

    private static void notifyBridge(MinecraftServer server, HarmonicBridgeState.Bridge bridge) {
        ServerLevel level = server.getLevel(bridge.dimension());
        if (level == null || !level.isLoaded(bridge.pos())) {
            return;
        }
        if (level.getBlockEntity(bridge.pos()) instanceof ExchangeBridgeBlockEntity block) {
            block.recordCrossing();
        }
    }
}
