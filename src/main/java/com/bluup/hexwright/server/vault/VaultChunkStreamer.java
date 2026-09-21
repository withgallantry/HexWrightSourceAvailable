package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.portal.PortalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VaultChunkStreamer {

    private static final int SUBSCRIPTION_INTERVAL = 10;

    private static final int CHUNKS_PER_TICK = 6;

    private static final int ENTITY_INTERVAL = 3;

    private static final double SUBSCRIBE_RANGE = 48.0;

    private static final double OUTSIDE_ENTITY_RANGE = 24.0;

    private static final int PENDING_RETRY_TICKS = 200;

    private static long tickCounter;

    private record PendingSend(VaultPortalSession session, ResourceKey<Level> dimension,
                               ArrayDeque<ChunkPos> queue, long retryDeadline) {

        static PendingSend of(VaultPortalSession session, ServerLevel level, List<ChunkPos> chunks) {
            return new PendingSend(session, level.dimension(), new ArrayDeque<>(chunks),
                tickCounter + PENDING_RETRY_TICKS);
        }
    }

    private static final Map<UUID, List<PendingSend>> PENDING = new HashMap<>();

    private static void queue(ServerPlayer player, PendingSend send) {
        List<PendingSend> owed = PENDING.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        owed.removeIf(p -> p.session() == send.session() && p.dimension().equals(send.dimension()));
        owed.add(send);
    }

    private static void cancel(UUID playerId, VaultPortalSession session, ResourceKey<Level> dimension) {
        List<PendingSend> owed = PENDING.get(playerId);
        if (owed != null && owed.removeIf(p -> p.session() == session && p.dimension().equals(dimension))
            && owed.isEmpty()) {
            PENDING.remove(playerId);
        }
    }

    private static final Map<UUID, Set<ResourceLocation>> RETAINED = new HashMap<>();

    public static void setRetained(UUID playerId, ResourceLocation dimension, boolean retained) {
        if (retained) {
            RETAINED.computeIfAbsent(playerId, id -> new HashSet<>()).add(dimension);
        } else {
            Set<ResourceLocation> held = RETAINED.get(playerId);
            if (held != null && held.remove(dimension) && held.isEmpty()) {
                RETAINED.remove(playerId);
            }
        }
    }

    private static boolean hasRetained(UUID playerId, ServerLevel level) {
        Set<ResourceLocation> held = RETAINED.get(playerId);
        return held != null && held.contains(level.dimension().location());
    }

    private VaultChunkStreamer() {
    }

    static void tick(MinecraftServer server) {
        tickCounter++;
        drainPending(server);
        boolean refreshSubscriptions = tickCounter % SUBSCRIPTION_INTERVAL == 0;
        boolean snapshotEntities = tickCounter % ENTITY_INTERVAL == 0;
        if (!refreshSubscriptions && !snapshotEntities) {
            return;
        }
        for (VaultPortalSession session : List.copyOf(VaultManager.activeSessions())) {
            ServerLevel vaultLevel = VaultDimension.level(server);
            ServerLevel outsideLevel = server.getLevel(session.outsideDimension());
            if (vaultLevel == null || outsideLevel == null) {
                continue;
            }
            if (refreshSubscriptions) {
                updateSubscriptions(server, session, vaultLevel, outsideLevel);
            }
            if (snapshotEntities) {
                snapshotEntities(server, session, vaultLevel, outsideLevel);
            }
        }
    }


    private static void updateSubscriptions(MinecraftServer server, VaultPortalSession session,
                                            ServerLevel vaultLevel, ServerLevel outsideLevel) {
        Set<UUID> desiredVaultViewers = new HashSet<>();
        double rangeSq = SUBSCRIBE_RANGE * SUBSCRIBE_RANGE;
        Vec3 outsideCenter = session.outsideWindow().center();
        for (ServerPlayer player : outsideLevel.players()) {
            if (player.position().distanceToSqr(outsideCenter) <= rangeSq) {
                desiredVaultViewers.add(player.getUUID());
            }
        }
        Set<UUID> desiredOutsideViewers = new HashSet<>(session.occupants());

        diffSubscribers(server, session.vaultViewers(), desiredVaultViewers,
            added -> {
                HexwrightNetworking.sendVaultLevelInit(added, vaultLevel);
                queue(added, PendingSend.of(session, vaultLevel, vaultViewChunks(session)));
            },
            removed -> {
                cancel(removed.getUUID(), session, vaultLevel.dimension());
                HexwrightNetworking.sendVaultChunkForget(removed, vaultLevel.dimension(),
                    vaultViewChunks(session));
            });

        diffSubscribers(server, session.outsideViewers(), desiredOutsideViewers,
            added -> {
                if (hasRetained(added.getUUID(), outsideLevel)) {
                    return;
                }
                HexwrightNetworking.sendVaultLevelInit(added, outsideLevel);
                queue(added, PendingSend.of(session, outsideLevel, session.outsideChunks()));
            },
            removed -> {
                cancel(removed.getUUID(), session, outsideLevel.dimension());
                HexwrightNetworking.sendVaultChunkForget(removed, outsideLevel.dimension(),
                    session.outsideChunks());
            });
    }

    private static void drainPending(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            int sent = 0;
            var owed = entry.getValue().iterator();
            while (owed.hasNext()) {
                PendingSend pending = owed.next();
                ServerLevel level = server.getLevel(pending.dimension());
                if (level == null) {
                    owed.remove();
                    continue;
                }
                var queue = pending.queue();
                boolean retry = tickCounter < pending.retryDeadline();
                int polls = queue.size();
                while (sent < CHUNKS_PER_TICK && polls-- > 0 && !queue.isEmpty()) {
                    ChunkPos pos = queue.poll();
                    if (sendChunk(player, level, pos)) {
                        sent++;
                    } else if (retry) {
                        queue.addLast(pos);
                    }
                }
                if (queue.isEmpty()) {
                    owed.remove();
                }
                if (sent >= CHUNKS_PER_TICK) {
                    break;
                }
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private interface SubscriberAction {
        void apply(ServerPlayer player);
    }

    private static void diffSubscribers(MinecraftServer server, Set<UUID> current, Set<UUID> desired,
                                        SubscriberAction onAdd, SubscriberAction onRemove) {
        for (UUID id : List.copyOf(current)) {
            if (!desired.contains(id)) {
                current.remove(id);
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    onRemove.apply(player);
                }
            }
        }
        for (UUID id : desired) {
            if (current.add(id)) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    onAdd.apply(player);
                } else {
                    current.remove(id);
                }
            }
        }
    }

    static List<ChunkPos> vaultViewChunks(VaultPortalSession session) {
        return session.vaultChunks();
    }

    private static boolean sendChunk(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) {
            return false;
        }
        HexwrightNetworking.sendVaultChunk(player, level, chunk);
        return true;
    }


    public static void onBlockChanged(ServerLevel level, BlockPos pos) {
        if (VaultManager.activeSessions().isEmpty()) {
            return;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (VaultPortalSession session : VaultManager.activeSessions()) {
            if (VaultDimension.isVaultLevel(level)) {
                if (session.watchesVaultChunk(chunkX, chunkZ)) {
                    HexwrightNetworking.sendVaultBlockUpdate(
                        level, session.vaultViewers(), pos);
                }
            } else if (level.dimension().equals(session.outsideDimension())) {
                for (ChunkPos chunk : session.outsideChunks()) {
                    if (chunk.x == chunkX && chunk.z == chunkZ) {
                        HexwrightNetworking.sendVaultBlockUpdate(
                            level, session.outsideViewers(), pos);
                        break;
                    }
                }
            }
        }
    }

    private static void snapshotEntities(MinecraftServer server, VaultPortalSession session,
                                         ServerLevel vaultLevel, ServerLevel outsideLevel) {
        if (!session.vaultViewers().isEmpty()) {
            AABB room = session.roomBounds();
            HexwrightNetworking.sendVaultEntities(server, vaultLevel, chunkOf(room.getCenter()),
                session.vaultViewers(), gatherEntities(vaultLevel, room));
        }
        if (!session.outsideViewers().isEmpty()) {
            Vec3 c = session.outsideWindow().center();
            AABB outsideBox = new AABB(
                c.x - OUTSIDE_ENTITY_RANGE, c.y - OUTSIDE_ENTITY_RANGE, c.z - OUTSIDE_ENTITY_RANGE,
                c.x + OUTSIDE_ENTITY_RANGE, c.y + OUTSIDE_ENTITY_RANGE, c.z + OUTSIDE_ENTITY_RANGE);
            HexwrightNetworking.sendVaultEntities(server, outsideLevel, chunkOf(c),
                session.outsideViewers(), gatherEntities(outsideLevel, outsideBox));
        }
    }

    private static ChunkPos chunkOf(Vec3 point) {
        return new ChunkPos(BlockPos.containing(point));
    }

    private static List<Entity> gatherEntities(ServerLevel level, AABB bounds) {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, bounds,
            e -> PortalManager.foldsThrough(e) && !e.isRemoved() && !e.isSpectator())) {
            entities.add(entity);
        }
        return entities;
    }


    static void onDoorRelocated(MinecraftServer server, VaultPortalSession session,
                                @Nullable ServerLevel oldOutsideLevel) {
        for (UUID id : session.vaultViewers()) {
            cancel(id, session, VaultDimension.KEY);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                HexwrightNetworking.sendVaultChunkForget(player, VaultDimension.KEY,
                    vaultViewChunks(session));
            }
        }
        for (UUID id : session.outsideViewers()) {
            if (oldOutsideLevel != null) {
                cancel(id, session, oldOutsideLevel.dimension());
                setRetained(id, oldOutsideLevel.dimension().location(), false);
            }
        }
        session.vaultViewers().clear();
        session.outsideViewers().clear();
    }

    static void release(MinecraftServer server, VaultPortalSession session) {
        for (UUID id : session.outsideViewers()) {
            cancel(id, session, session.outsideDimension());
        }
        for (UUID id : session.vaultViewers()) {
            cancel(id, session, VaultDimension.KEY);
        }
        for (UUID id : session.vaultViewers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                HexwrightNetworking.sendVaultChunkForget(player, VaultDimension.KEY,
                    vaultViewChunks(session));
            }
        }
        for (UUID id : session.outsideViewers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                HexwrightNetworking.sendVaultChunkForget(player, session.outsideDimension(),
                    session.outsideChunks());
            }
        }
        session.vaultViewers().clear();
        session.outsideViewers().clear();
    }

    static void forget(UUID playerId) {
        PENDING.remove(playerId);
        RETAINED.remove(playerId);
    }
}
