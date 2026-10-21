package com.bluup.hexwright.server.animation;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FlyingPlayers {

    private static final Set<UUID> FLYING = new HashSet<>();

    private FlyingPlayers() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean flying = player.getAbilities().flying;
                boolean changed = flying ? FLYING.add(player.getUUID()) : FLYING.remove(player.getUUID());
                if (changed) {
                    for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
                        if (watcher != player) {
                            HexwrightNetworking.sendPlayerFlying(watcher, player, flying);
                        }
                    }
                }
            }
        });
        EntityTrackingEvents.START_TRACKING.register((tracked, watcher) -> {
            if (tracked instanceof ServerPlayer player && player != watcher && player.getAbilities().flying) {
                HexwrightNetworking.sendPlayerFlying(watcher, player, true);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FLYING.remove(handler.player.getUUID()));
    }
}
