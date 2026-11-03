package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.server.sky.CellSkies;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class DungeonSkies {

    private DungeonSkies() {
    }

    static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> syncTo(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncTo(newPlayer));
    }

    static List<CellSkies.Region> all(MinecraftServer server) {
        int half = DungeonInstances.CELL_SPACING / 2;
        List<CellSkies.Region> skies = new ArrayList<>();
        for (DungeonInstance instance : DungeonInstanceRegistry.get(server).all()) {
            ResourceLocation sky = instance.kind().sky();
            if (sky == null || instance.state() != DungeonInstance.State.OPEN) {
                continue;
            }
            BlockPos origin = DungeonInstances.cellOrigin(instance.cell());
            skies.add(new CellSkies.Region(origin.getX() - half, origin.getZ() - half,
                origin.getX() + half, origin.getZ() + half, sky));
        }
        return skies;
    }

    static void syncTo(ServerPlayer player) {
        if (DungeonDimension.isDungeonLevel(player.level())) {
            CellSkies.send(player, DungeonDimension.KEY, all(player.server));
        }
    }

    static void broadcast(MinecraftServer server) {
        ServerLevel dungeons = DungeonDimension.level(server);
        if (dungeons == null || dungeons.players().isEmpty()) {
            return;
        }
        List<CellSkies.Region> skies = all(server);
        for (ServerPlayer player : dungeons.players()) {
            CellSkies.send(player, DungeonDimension.KEY, skies);
        }
    }
}
