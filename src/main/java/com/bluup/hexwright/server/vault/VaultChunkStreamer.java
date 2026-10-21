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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class VaultChunkStreamer {

    private static final int SUBSCRIPTION_INTERVAL = 10;

    private static final int CHUNKS_PER_TICK = 6;

    private static final int ENTITY_INTERVAL = 3;

    private static final double SUBSCRIBE_RANGE = 48.0;

    private static final double OUTSIDE_ENTITY_RANGE = 24.0;

    private static final int PENDING_RETRY_TICKS = 200;

    private static long tickCounter;

    private record PendingSend(StreamedPortal session, ResourceKey<Level> dimension,
                               ArrayDeque<ChunkPos> queue, long retryDeadline) {

        static PendingSend of(StreamedPortal session, ServerLevel level, List<ChunkPos> chunks) {
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

    private static void cancel(UUID playerId, StreamedPortal session, ResourceKey<Level> dimension) {
        List<PendingSend> owed = PENDING.get(playerId);
        if (owed != null && owed.removeIf(p -> p.session() == session && p.dimension().equals(dimension))
            && owed.isEmpty()) {
            PENDING.remove(playerId);
        }
    }

    private record Retained(ChunkPos center, int radius) {
    }

    private static final Map<UUID, Map<ResourceLocation, Retained>> RETAINED = new HashMap<>();

    public static void setRetained(UUID playerId, ResourceLocation dimension, ChunkPos center, int radius) {
        RETAINED.computeIfAbsent(playerId, id -> new HashMap<>()).put(dimension, new Retained(center, radius));
    }

    public static void dropRetained(UUID playerId, ResourceLocation dimension) {
        Map<ResourceLocation, Retained> held = RETAINED.get(playerId);
        if (held != null && held.remove(dimension) != null && held.isEmpty()) {
            RETAINED.remove(playerId);
        }
    }

    private static @Nullable Retained retained(UUID playerId, ResourceKey<Level> dimension) {
        Map<ResourceLocation, Retained> held = RETAINED.get(playerId);
        return held == null ? null : held.get(dimension.location());
    }

    private static boolean retainedCovers(UUID playerId, StreamedPortal session) {
        Retained kept = retained(playerId, session.outsideDimension());
        if (kept == null) {
            return false;
        }
        ChunkPos mouth = chunkOf(session.outsideWindow().center());
        int distance = Math.max(Math.abs(mouth.x - kept.center().x), Math.abs(mouth.z - kept.center().z));
        return distance + VaultManager.OUTSIDE_WATCH_RADIUS_CHUNKS <= kept.radius();
    }

    private static final List<Supplier<? extends Collection<? extends StreamedPortal>>> SOURCES = new ArrayList<>();

    private VaultChunkStreamer() {
    }

    public static void addSource(Supplier<? extends Collection<? extends StreamedPortal>> source) {
        SOURCES.add(source);
    }

    private static List<StreamedPortal> doors() {
        List<StreamedPortal> doors = new ArrayList<>();
        for (Supplier<? extends Collection<? extends StreamedPortal>> source : SOURCES) {
            doors.addAll(source.get());
        }
        return doors;
    }

    private static boolean noDoors() {
        for (Supplier<? extends Collection<? extends StreamedPortal>> source : SOURCES) {
            if (!source.get().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static void tick(MinecraftServer server) {
        tickCounter++;
        drainPending(server);
        boolean refreshSubscriptions = tickCounter % SUBSCRIPTION_INTERVAL == 0;
        boolean snapshotEntities = tickCounter % ENTITY_INTERVAL == 0;
        if (!refreshSubscriptions && !snapshotEntities) {
            return;
        }
        for (StreamedPortal session : doors()) {
            ServerLevel vaultLevel = server.getLevel(session.insideDimension());
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


    private static void updateSubscriptions(MinecraftServer server, StreamedPortal session,
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

        diffSubscribers(server, session.insideViewers(), desiredVaultViewers,
            added -> {
                HexwrightNetworking.sendVaultLevelInit(added, vaultLevel);
                queue(added, PendingSend.of(session, vaultLevel, session.insideChunks()));
            },
            removed -> {
                cancel(removed.getUUID(), session, vaultLevel.dimension());
                forgetUnshared(removed, session, vaultLevel.dimension(), session.insideChunks());
            });

        diffSubscribers(server, session.outsideViewers(), desiredOutsideViewers,
            added -> {
                if (retainedCovers(added.getUUID(), session)) {
                    return;
                }
                HexwrightNetworking.sendVaultLevelInit(added, outsideLevel);
                queue(added, PendingSend.of(session, outsideLevel, session.outsideChunks()));
            },
            removed -> {
                cancel(removed.getUUID(), session, outsideLevel.dimension());
                forgetUnshared(removed, session, outsideLevel.dimension(), session.outsideChunks());
            });
    }

    private static void forgetUnshared(ServerPlayer player, StreamedPortal leaving,
                                       ResourceKey<Level> dimension, List<ChunkPos> chunks) {
        UUID id = player.getUUID();
        Set<Long> stillShown = new HashSet<>();
        for (StreamedPortal other : doors()) {
            if (other == leaving) {
                continue;
            }
            if (dimension.equals(other.insideDimension()) && other.insideViewers().contains(id)) {
                for (ChunkPos chunk : other.insideChunks()) {
                    stillShown.add(chunk.toLong());
                }
            }
            if (dimension.equals(other.outsideDimension()) && other.outsideViewers().contains(id)) {
                for (ChunkPos chunk : other.outsideChunks()) {
                    stillShown.add(chunk.toLong());
                }
            }
        }
        List<ChunkPos> forgotten = new ArrayList<>(chunks.size());
        for (ChunkPos chunk : chunks) {
            if (!stillShown.contains(chunk.toLong())) {
                forgotten.add(chunk);
            }
        }
        if (!forgotten.isEmpty()) {
            HexwrightNetworking.sendVaultChunkForget(player, dimension, forgotten);
        }
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

    private static boolean sendChunk(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) {
            return false;
        }
        HexwrightNetworking.sendVaultChunk(player, level, chunk);
        return true;
    }


    public static void onBlockChanged(ServerLevel level, BlockPos pos) {
        if (noDoors()) {
            return;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (StreamedPortal session : doors()) {
            if (level.dimension().equals(session.insideDimension())) {
                if (session.watchesInsideChunk(chunkX, chunkZ)) {
                    HexwrightNetworking.sendVaultBlockUpdate(
                        level, session.insideViewers(), pos);
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

    private static void snapshotEntities(MinecraftServer server, StreamedPortal session,
                                         ServerLevel vaultLevel, ServerLevel outsideLevel) {
        if (!session.insideViewers().isEmpty()) {
            AABB room = session.insideBounds();
            HexwrightNetworking.sendVaultEntities(server, vaultLevel, chunkOf(room.getCenter()),
                session.insideViewers(), gatherEntities(vaultLevel, room));
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
        for (UUID id : session.insideViewers()) {
            cancel(id, session, session.insideDimension());
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                forgetUnshared(player, session, session.insideDimension(), session.insideChunks());
            }
        }
        for (UUID id : session.outsideViewers()) {
            if (oldOutsideLevel != null) {
                cancel(id, session, oldOutsideLevel.dimension());
            }
        }
        session.insideViewers().clear();
        session.outsideViewers().clear();
    }

    public static void release(MinecraftServer server, StreamedPortal session) {
        for (UUID id : session.outsideViewers()) {
            cancel(id, session, session.outsideDimension());
        }
        for (UUID id : session.insideViewers()) {
            cancel(id, session, session.insideDimension());
        }
        for (UUID id : session.insideViewers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                forgetUnshared(player, session, session.insideDimension(), session.insideChunks());
            }
        }
        for (UUID id : session.outsideViewers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && retained(id, session.outsideDimension()) == null) {
                forgetUnshared(player, session, session.outsideDimension(), session.outsideChunks());
            }
        }
        session.insideViewers().clear();
        session.outsideViewers().clear();
    }

    public static void forget(UUID playerId) {
        for (StreamedPortal door : doors()) {
            door.insideViewers().remove(playerId);
            door.outsideViewers().remove(playerId);
        }
        PENDING.remove(playerId);
        RETAINED.remove(playerId);
    }
}
