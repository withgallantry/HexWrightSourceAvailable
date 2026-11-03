package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class DungeonRifts {

    public static final ResourceLocation CHANNEL = Hexwright.id("dungeon_rifts_s2c");

    private DungeonRifts() {
    }

    static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> syncTo(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncTo(newPlayer));
    }

    static List<DungeonRift> in(MinecraftServer server, ResourceKey<Level> dimension) {
        long now = server.overworld().getGameTime();
        boolean dungeons = dimension.equals(DungeonDimension.KEY);
        List<DungeonRift> rifts = new ArrayList<>();
        for (DungeonInstance instance : DungeonInstanceRegistry.get(server).all()) {
            DungeonRift entrance = instance.entranceRift(now);
            if (entrance != null && instance.entrance().dimension().equals(dimension)) {
                rifts.add(entrance);
            }
            if (dungeons && instance.state() == DungeonInstance.State.OPEN && !instance.windowHolds(now)
                && !instance.windowForming(now)) {
                rifts.add(instance.safeRoom().exit(instance.id(), ~instance.seed()));
            }
        }
        return rifts;
    }

    static void syncTo(ServerPlayer player) {
        send(player, in(player.server, player.level().dimension()));
    }

    static void broadcast(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return;
        }
        List<DungeonRift> rifts = in(server, dimension);
        for (ServerPlayer player : PlayerLookup.world(level)) {
            send(player, rifts);
        }
    }

    private static void send(ServerPlayer player, List<DungeonRift> rifts) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeCollection(rifts, (out, rift) -> rift.write(out));
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
}
