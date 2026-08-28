package com.bluup.hexwright.server.worldgen.ruinedportal;

import com.bluup.hexwright.server.block.RuinedPortalFrameBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RuinedPortalManager {

    private static final double SCAN_RANGE = 12.0;

    private static final double HALF_WIDTH = 1.5;
    private static final double HEIGHT = 3.0;
    private static final double HALF_DEPTH = 0.9;

    private static final long COOLDOWN_TICKS = 100;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> PORTALS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private RuinedPortalManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RuinedPortalManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            COOLDOWNS.remove(handler.player.getUUID()));
    }

    public static void markLoaded(ResourceKey<Level> dimension, BlockPos pos) {
        PORTALS.computeIfAbsent(dimension, key -> new HashSet<>()).add(pos.immutable());
    }

    public static void unmark(ResourceKey<Level> dimension, BlockPos pos) {
        Set<BlockPos> positions = PORTALS.get(dimension);
        if (positions != null) {
            positions.remove(pos);
        }
    }

    private static AABB triggerBox(BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        return new AABB(cx - HALF_WIDTH, pos.getY(), cz - HALF_DEPTH,
            cx + HALF_WIDTH, pos.getY() + HEIGHT, cz + HALF_DEPTH);
    }

    private static void tick(MinecraftServer server) {
        if (PORTALS.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || onCooldown(player)) {
                continue;
            }
            Set<BlockPos> positions = PORTALS.get(player.level().dimension());
            if (positions == null || positions.isEmpty()) {
                continue;
            }
            BlockPos playerPos = player.blockPosition();
            for (BlockPos pos : positions) {
                if (pos.distSqr(playerPos) > SCAN_RANGE * SCAN_RANGE) {
                    continue;
                }
                if (!player.getBoundingBox().intersects(triggerBox(pos))) {
                    continue;
                }
                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (blockEntity instanceof RuinedPortalFrameBlockEntity frame) {
                    RuinedPortalOutcomeResolver.trigger(player, frame);
                    markCooldown(player);
                }
                break;
            }
        }
    }

    private static boolean onCooldown(ServerPlayer player) {
        Long last = COOLDOWNS.get(player.getUUID());
        return last != null && player.level().getGameTime() - last < COOLDOWN_TICKS;
    }

    private static void markCooldown(ServerPlayer player) {
        COOLDOWNS.put(player.getUUID(), player.level().getGameTime());
    }
}
