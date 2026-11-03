package com.bluup.hexwright.server.harmonic;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class HarmonicEvents {

    private static final long RECONCILE_INTERVAL = 20L;

    private HarmonicEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            HarmonicDelivery.drain(server);
            if (server.overworld().getGameTime() % RECONCILE_INTERVAL == 0L) {
                HarmonicSubscriptions.refreshAll(server);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            HarmonicSubscriptions.refresh(handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            HarmonicSubscriptions.unregister(server, handler.player.getUUID()));

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (player.getServer() != null) {
                HarmonicSubscriptions.unregister(player.getServer(), player.getUUID());
            }
            HarmonicSubscriptions.refresh(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer.getServer() != null) {
                HarmonicSubscriptions.unregister(newPlayer.getServer(), newPlayer.getUUID());
            }
            HarmonicSubscriptions.refresh(newPlayer);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            HarmonicSubscriptions.clearAll();
            HarmonicEmitterRegistry.clearAll();
        });
    }
}
