package com.bluup.hexwright.server.portal;

import com.bluup.hexwright.server.vault.VaultChunkStreamer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class JanusSeams {

    static final TicketType<UUID> TICKET = TicketType.create("hexwright_janus_seam", UUID::compareTo);

    private static final Map<UUID, JanusSeamView> VIEWS = new HashMap<>();

    private JanusSeams() {
    }

    public static void register() {
        VaultChunkStreamer.addSource(VIEWS::values);
        ServerTickEvents.END_SERVER_TICK.register(JanusSeams::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VIEWS.clear());
    }

    private static void tick(MinecraftServer server) {
        Map<UUID, PortalPair> open = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (PortalPair pair : PortalManager.get(level).pairs()) {
                if (pair.isCrossDimensionalSeam()) {
                    open.putIfAbsent(pair.id(), pair);
                }
            }
        }
        var iterator = VIEWS.values().iterator();
        while (iterator.hasNext()) {
            JanusSeamView view = iterator.next();
            if (!open.containsKey(view.pairId())) {
                view.releaseTickets(server);
                VaultChunkStreamer.release(server, view);
                iterator.remove();
            }
        }
        for (PortalPair pair : open.values()) {
            JanusSeamView view = VIEWS.computeIfAbsent(pair.id(), id -> new JanusSeamView(pair));
            view.updateOccupants(server);
            view.updateTickets(server);
        }
    }
}
