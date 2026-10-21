package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.sky.CellSkies;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class VaultSkies {

    public static final List<String> CHOICES = List.of(
        "azure_noon",
        "golden_hour",
        "pastel_dawn",
        "hex_lattice",
        "orrery",
        "prism_drift",
        "crimson_nebula",
        "teal_magenta_nebula",
        "binary_suns",
        "brass_nebula");

    private static final int MARGIN = 16;

    private VaultSkies() {
    }

    static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            CellSkies.send(handler.player, VaultDimension.KEY, all(server)));
    }

    public static void set(MinecraftServer server, VaultRecord record, @Nullable String sky) {
        record.setSky(sky == null ? "" : sky);
        VaultRegistry.get(server).setDirty();
        List<CellSkies.Region> regions = all(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CellSkies.send(player, VaultDimension.KEY, regions);
        }
    }

    private static List<CellSkies.Region> all(MinecraftServer server) {
        List<CellSkies.Region> regions = new ArrayList<>();
        for (VaultRecord record : VaultRegistry.get(server).all()) {
            if (record.sky().isEmpty()) {
                continue;
            }
            AABB room = VaultRooms.roomBounds(record);
            regions.add(new CellSkies.Region((int) room.minX - MARGIN, (int) room.minZ - MARGIN,
                (int) room.maxX + MARGIN, (int) room.maxZ + MARGIN, Hexwright.id(record.sky())));
        }
        return regions;
    }
}
