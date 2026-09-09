package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.portal.PortalManager;
import com.bluup.hexwright.server.portal.PortalPair;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class VaultManager {

    public static final int MAX_ACTIVE_SESSIONS_PER_VAULT = 1;

    public static final int MAX_REMOTE_CHUNKS_PER_VAULT_VIEW = 64;

    public static final int OUTSIDE_WATCH_RADIUS_CHUNKS = Mth.clamp(
        Integer.getInteger("hexwright.vault.outsideRadius", 4), 1, 8);

    public static final int OUTSIDE_TICKET_RADIUS_CHUNKS = 2;

    public static final double PORTAL_KEEPALIVE_DISTANCE = 32.0;

    public static final int CLOSE_GRACE_TICKS = 100;

    private static final int REOPEN_SEARCH_RADIUS = 4;

    private static final boolean DEBUG = "true".equals(System.getProperty("hexwright.vault.debug"));

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("hexwright_vault", Comparator.comparingLong(ChunkPos::toLong));

    private static final int VIEW_TICKET_RADIUS = 2;

    private static final Map<Integer, VaultPortalSession> SESSIONS_BY_VAULT = new HashMap<>();
    private static final Map<UUID, VaultPortalSession> SESSIONS_BY_OPENER = new HashMap<>();

    private VaultManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(VaultManager::tick);
        PortalManager.setCrossingGuard(VaultAccess::mayCross);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!VaultDimension.isVaultLevel(level)
                || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("hexwright.vault.no_sleeping"), true);
            }
            return InteractionResult.SUCCESS;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (VaultPortalSession session : List.copyOf(SESSIONS_BY_VAULT.values())) {
                reconcileOccupants(server, session);
                for (UUID occupant : List.copyOf(session.occupants())) {
                    ServerPlayer player = server.getPlayerList().getPlayer(occupant);
                    if (player != null) {
                        depositAtEntrance(player);
                    }
                }
                finalizeClose(server, session, "server stopping");
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            VaultCommands.register(dispatcher));
    }


    public static VaultRecord createVault(ServerPlayer owner, PocketCasterData.Quality grade,
                                          boolean artifact, @Nullable String build) {
        return VaultRegistry.get(owner.server).create(owner.server, owner, grade, artifact, build);
    }

    public static @Nullable VaultRecord getVault(MinecraftServer server, int vaultId) {
        return VaultRegistry.get(server).byId(vaultId);
    }

    public static List<VaultRecord> getVaults(MinecraftServer server, UUID creator) {
        return VaultRegistry.get(server).byCreator(creator);
    }

    public static @Nullable VaultPortalSession sessionByOpener(UUID opener) {
        return SESSIONS_BY_OPENER.get(opener);
    }

    public static @Nullable VaultPortalSession sessionByVault(int vaultId) {
        return SESSIONS_BY_VAULT.get(vaultId);
    }

    public static @Nullable VaultPortalSession sessionByPair(UUID pairId) {
        for (VaultPortalSession session : SESSIONS_BY_VAULT.values()) {
            if (session.pairId().equals(pairId)) {
                return session;
            }
        }
        return null;
    }

    public static Collection<VaultPortalSession> activeSessions() {
        return SESSIONS_BY_VAULT.values();
    }

    public static boolean openVault(ServerPlayer player, int vaultId, PortalWindow outsideWindow) {
        MinecraftServer server = player.server;
        VaultRegistry registry = VaultRegistry.get(server);
        VaultRecord record = registry.byId(vaultId);
        if (record == null) {
            player.displayClientMessage(Component.translatable("hexwright.vault.unknown", vaultId), true);
            return false;
        }
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (vaultLevel == null) {
            player.displayClientMessage(Component.translatable("hexwright.vault.no_dimension"), true);
            return false;
        }
        if (VaultDimension.isVaultLevel(player.serverLevel())) {
            player.displayClientMessage(Component.translatable("hexwright.vault.inside_vault"), true);
            return false;
        }

        VaultPortalSession mine = SESSIONS_BY_OPENER.get(player.getUUID());
        if (mine != null && mine.vaultId() == vaultId) {
            requestClose(server, mine, player);
            return true;
        }
        VaultPortalSession standing = SESSIONS_BY_VAULT.get(vaultId);
        if (standing != null) {
            reconcileOccupants(server, standing);
            if (!standing.occupants().isEmpty()) {
                player.displayClientMessage(
                    Component.translatable("hexwright.vault.occupied", vaultId), true);
                return false;
            }
        }
        if (mine != null) {
            reconcileOccupants(server, mine);
            if (!mine.occupants().isEmpty()) {
                player.displayClientMessage(Component.translatable("hexwright.vault.occupied_replace"), true);
                return false;
            }
        }
        if (standing != null) {
            ServerPlayer previous = server.getPlayerList().getPlayer(standing.opener());
            if (previous != null && previous != player) {
                previous.displayClientMessage(
                    Component.translatable("hexwright.vault.door_moved", vaultId), false);
            }
            finalizeClose(server, standing, "door moved by " + player.getGameProfile().getName());
        }
        if (mine != null && mine != standing) {
            finalizeClose(server, mine, "replaced by vault " + vaultId);
        }

        ensureRoomIntact(vaultLevel, record);
        openSession(server, vaultLevel, record, player.getUUID(), player.serverLevel(), outsideWindow);
        player.displayClientMessage(Component.translatable("hexwright.vault.opened", vaultId), true);
        return true;
    }

    private static VaultPortalSession openSession(MinecraftServer server, ServerLevel vaultLevel,
                                                  VaultRecord record, UUID opener,
                                                  ServerLevel outsideLevel, PortalWindow outsideWindow) {
        PortalWindow vaultWindow = VaultRooms.vaultWindow(record);
        List<ChunkPos> outsideChunks = outsideChunksFor(outsideWindow, OUTSIDE_WATCH_RADIUS_CHUNKS);
        List<ChunkPos> outsideTicketChunks = outsideChunksFor(outsideWindow, OUTSIDE_TICKET_RADIUS_CHUNKS);
        ChunkPos vaultChunk = VaultRooms.roomChunk(record);
        int vaultTicketRadius = VaultRooms.roomTicketRadius(record);
        List<ChunkPos> vaultChunks = VaultRooms.viewChunks(record);

        vaultLevel.getChunkSource().addRegionTicket(TICKET, vaultChunk, vaultTicketRadius, vaultChunk);
        for (ChunkPos chunk : outsideTicketChunks) {
            outsideLevel.getChunkSource().addRegionTicket(TICKET, chunk, VIEW_TICKET_RADIUS, chunk);
        }

        PortalPair pair = new PortalPair(UUID.randomUUID(), opener,
            outsideWindow, vaultWindow, outsideLevel.getGameTime(),
            outsideLevel.dimension(), VaultDimension.KEY, true);
        PortalManager.get(outsideLevel).addPair(outsideLevel, pair);
        PortalManager.get(vaultLevel).addPair(vaultLevel, pair);

        VaultPortalSession session = new VaultPortalSession(record.id(), opener, pair.id(),
            outsideLevel.dimension(), outsideWindow, vaultWindow,
            outsideChunks, outsideTicketChunks, vaultChunk, vaultTicketRadius, vaultChunks,
            VaultRooms.roomBounds(record));
        SESSIONS_BY_VAULT.put(record.id(), session);
        SESSIONS_BY_OPENER.put(opener, session);
        rememberDoor(server, record, outsideLevel, outsideWindow);
        if (DEBUG) {
            Hexwright.LOGGER.info("[vault] session {} opened vault {} for {}",
                session.sessionId(), record.id(), opener);
        }
        return session;
    }

    private static void rememberDoor(MinecraftServer server, VaultRecord record,
                                     ServerLevel outsideLevel, PortalWindow outsideWindow) {
        record.setLastDoor(new VaultRecord.Door(outsideLevel.dimension(),
            VaultPortalPlacement.anchorOf(outsideWindow),
            VaultPortalPlacement.facingOf(outsideWindow)));
        VaultRegistry.get(server).setDirty();
    }

    public static boolean rebuildVault(MinecraftServer server, VaultRecord record) {
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (vaultLevel == null) {
            return false;
        }
        for (ChunkPos chunk : VaultRooms.roomChunks(record)) {
            vaultLevel.getChunk(chunk.x, chunk.z);
        }
        VaultRooms.generate(vaultLevel, record);
        Hexwright.LOGGER.info("Rebuilt vault {} ({}{})", record.id(), record.grade(),
            record.build().isEmpty() ? "" : "/" + record.build());
        return true;
    }

    public static boolean relocateDoor(MinecraftServer server, VaultRecord record, PortalWindow newWindow) {
        VaultPortalSession session = SESSIONS_BY_VAULT.get(record.id());
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (session == null || vaultLevel == null) {
            return false;
        }
        ServerLevel newOutsideLevel = server.overworld();

        ServerLevel oldOutsideLevel = server.getLevel(session.outsideDimension());
        if (oldOutsideLevel != null) {
            PortalManager.get(oldOutsideLevel).removePair(oldOutsideLevel, session.pairId());
            for (ChunkPos chunk : session.outsideTicketChunks()) {
                oldOutsideLevel.getChunkSource().removeRegionTicket(TICKET, chunk, VIEW_TICKET_RADIUS, chunk);
            }
        }
        PortalManager.get(vaultLevel).removePair(vaultLevel, session.pairId());

        List<ChunkPos> outsideChunks = outsideChunksFor(newWindow, OUTSIDE_WATCH_RADIUS_CHUNKS);
        List<ChunkPos> outsideTicketChunks = outsideChunksFor(newWindow, OUTSIDE_TICKET_RADIUS_CHUNKS);
        for (ChunkPos chunk : outsideTicketChunks) {
            newOutsideLevel.getChunkSource().addRegionTicket(TICKET, chunk, VIEW_TICKET_RADIUS, chunk);
        }

        VaultChunkStreamer.onDoorRelocated(server, session, oldOutsideLevel);

        PortalPair newPair = new PortalPair(UUID.randomUUID(), session.opener(),
            newWindow, session.vaultWindow(), newOutsideLevel.getGameTime(),
            newOutsideLevel.dimension(), VaultDimension.KEY, true);
        PortalManager.get(newOutsideLevel).addPair(newOutsideLevel, newPair);
        PortalManager.get(vaultLevel).addPair(vaultLevel, newPair);

        VaultPortalSession relocated = new VaultPortalSession(record.id(), session.opener(), newPair.id(),
            newOutsideLevel.dimension(), newWindow, session.vaultWindow(),
            outsideChunks, outsideTicketChunks,
            session.vaultChunk(), session.vaultTicketRadius(), session.vaultChunks(),
            session.roomBounds());
        SESSIONS_BY_VAULT.put(record.id(), relocated);
        SESSIONS_BY_OPENER.put(session.opener(), relocated);
        rememberDoor(server, record, newOutsideLevel, newWindow);
        if (DEBUG) {
            Hexwright.LOGGER.info("[vault] session {} (vault {}) door relocated by pattern",
                relocated.sessionId(), record.id());
        }
        return true;
    }

    public static void requestCloseVault(ServerPlayer player) {
        VaultPortalSession session = SESSIONS_BY_OPENER.get(player.getUUID());
        if (session == null) {
            player.displayClientMessage(Component.translatable("hexwright.vault.none_open"), true);
            return;
        }
        requestClose(player.server, session, player);
    }

    private static void requestClose(MinecraftServer server, VaultPortalSession session,
                                     @Nullable ServerPlayer requester) {
        reconcileOccupants(server, session);
        if (session.occupants().isEmpty()) {
            finalizeClose(server, session, "close requested");
            if (requester != null) {
                requester.displayClientMessage(
                    Component.translatable("hexwright.vault.closed", session.vaultId()), true);
            }
            return;
        }
        session.setCloseRequested(true);
        session.setState(VaultPortalSession.State.CLOSE_PENDING_OCCUPANTS);
        if (requester != null) {
            requester.displayClientMessage(
                Component.translatable("hexwright.vault.close_pending", session.vaultId()), true);
        }
    }


    private static void tick(MinecraftServer server) {
        if (!SESSIONS_BY_VAULT.isEmpty()) {
            for (VaultPortalSession session : List.copyOf(SESSIONS_BY_VAULT.values())) {
                tickSession(server, session);
            }
            VaultChunkStreamer.tick(server);
        }
        VaultContainment.tick(server);
    }

    private static void tickSession(MinecraftServer server, VaultPortalSession session) {
        ServerLevel outsideLevel = server.getLevel(session.outsideDimension());
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (outsideLevel == null || vaultLevel == null) {
            finalizeClose(server, session, "end level vanished");
            return;
        }
        PortalPair pair = PortalManager.get(outsideLevel).byId(session.pairId());
        if (pair == null) {
            finalizeClose(server, session, "portal pair removed externally");
            return;
        }

        reconcileOccupants(server, session);
        boolean occupied = !session.occupants().isEmpty();

        if (session.state() == VaultPortalSession.State.OPENING && pair.isOpen(outsideLevel.getGameTime())) {
            session.setState(VaultPortalSession.State.OPEN);
        }

        if (session.closeRequested()) {
            if (occupied) {
                session.setState(VaultPortalSession.State.CLOSE_PENDING_OCCUPANTS);
            } else {
                finalizeClose(server, session, "deferred close completed");
            }
            return;
        }

        if (session.state() != VaultPortalSession.State.OPEN) {
            return;
        }

        boolean nearOutside = anyPlayerNear(outsideLevel, session.outsideWindow().center());
        boolean nearInside = anyPlayerNear(vaultLevel, session.vaultWindow().center());
        boolean keepOpen = occupied || nearOutside || nearInside;
        if (keepOpen) {
            session.setGraceTicks(0);
            return;
        }
        session.setGraceTicks(session.graceTicks() + 1);
        if (session.graceTicks() > CLOSE_GRACE_TICKS) {
            finalizeClose(server, session, "empty and unattended");
        }
    }

    private static boolean anyPlayerNear(ServerLevel level, Vec3 center) {
        double rangeSq = PORTAL_KEEPALIVE_DISTANCE * PORTAL_KEEPALIVE_DISTANCE;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.position().distanceToSqr(center) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private static void reconcileOccupants(MinecraftServer server, VaultPortalSession session) {
        VaultRegistry registry = VaultRegistry.get(server);
        VaultRecord record = registry.byId(session.vaultId());
        session.occupants().clear();
        if (record == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!VaultDimension.isVaultLevel(player.serverLevel())) {
                continue;
            }
            VaultRecord standing = registry.byPosition(player.position());
            if (standing != null && standing.id() == record.id()) {
                session.occupants().add(player.getUUID());
            }
        }
    }


    static void finalizeClose(MinecraftServer server, VaultPortalSession session, String reason) {
        if (session.state() == VaultPortalSession.State.CLOSED) {
            return;
        }
        session.setState(VaultPortalSession.State.CLOSED);
        SESSIONS_BY_VAULT.remove(session.vaultId(), session);
        SESSIONS_BY_OPENER.remove(session.opener(), session);

        ServerLevel outsideLevel = server.getLevel(session.outsideDimension());
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (outsideLevel != null) {
            PortalManager.get(outsideLevel).removePair(outsideLevel, session.pairId());
            for (ChunkPos chunk : session.outsideTicketChunks()) {
                outsideLevel.getChunkSource().removeRegionTicket(TICKET, chunk, VIEW_TICKET_RADIUS, chunk);
            }
        }
        if (vaultLevel != null) {
            PortalManager.get(vaultLevel).removePair(vaultLevel, session.pairId());
            vaultLevel.getChunkSource().removeRegionTicket(
                TICKET, session.vaultChunk(), session.vaultTicketRadius(), session.vaultChunk());
        }
        VaultChunkStreamer.release(server, session);
        if (DEBUG) {
            Hexwright.LOGGER.info("[vault] session {} (vault {}) closed: {}",
                session.sessionId(), session.vaultId(), reason);
        }
    }

    private static void onDisconnect(ServerPlayer player) {
        depositAtEntrance(player);
        for (VaultPortalSession any : SESSIONS_BY_VAULT.values()) {
            any.vaultViewers().remove(player.getUUID());
            any.outsideViewers().remove(player.getUUID());
        }
        VaultChunkStreamer.forget(player.getUUID());
        VaultContainment.forget(player.getUUID());
        VaultPortalSession session = SESSIONS_BY_OPENER.get(player.getUUID());
        if (session == null) {
            return;
        }
        reconcileOccupants(player.server, session);
        session.occupants().remove(player.getUUID());
        if (session.occupants().isEmpty()) {
            finalizeClose(player.server, session, "opener disconnected");
        } else {
            session.setCloseRequested(true);
            session.setState(VaultPortalSession.State.CLOSE_PENDING_OCCUPANTS);
        }
    }

    private static void onJoin(ServerPlayer player) {
        if (!VaultDimension.isVaultLevel(player.serverLevel())) {
            return;
        }
        MinecraftServer server = player.server;
        VaultRecord record = VaultRegistry.get(server).byPosition(player.position());
        if (record != null && SESSIONS_BY_VAULT.containsKey(record.id())) {
            return;
        }
        if (record != null && record.owner().equals(player.getUUID())) {
            if (reopenDoor(server, player, record)) {
                return;
            }
            player.displayClientMessage(
                Component.translatable("hexwright.vault.door_lost", record.id()), false);
        }
        depositAtEntrance(player);
    }

    private static boolean reopenDoor(MinecraftServer server, ServerPlayer owner, VaultRecord record) {
        VaultRecord.Door door = record.lastDoor();
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (door == null || vaultLevel == null) {
            return false;
        }
        ServerLevel outsideLevel = server.getLevel(door.dimension());
        if (outsideLevel == null || VaultDimension.isVaultLevel(outsideLevel)) {
            return false;
        }
        outsideLevel.getChunk(door.anchor().getX() >> 4, door.anchor().getZ() >> 4);
        PortalWindow window = VaultPortalPlacement.windowNear(
            outsideLevel, door.anchor(), door.facing(), REOPEN_SEARCH_RADIUS);
        if (window == null) {
            return false;
        }
        ensureRoomIntact(vaultLevel, record);
        openSession(server, vaultLevel, record, owner.getUUID(), outsideLevel, window);
        owner.displayClientMessage(
            Component.translatable("hexwright.vault.reopened", record.id()), true);
        return true;
    }


    private record Exit(ServerLevel level, Vec3 pos) {
    }

    private static Exit exitFor(ServerPlayer player) {
        return exitFor(player, null);
    }

    private static Exit exitFor(ServerPlayer player, @Nullable VaultRecord hint) {
        MinecraftServer server = player.server;
        VaultRecord record = hint != null
            ? hint
            : VaultRegistry.get(server).byPosition(player.position());
        VaultPortalSession session = record == null ? null : SESSIONS_BY_VAULT.get(record.id());
        if (session != null) {
            ServerLevel outsideLevel = server.getLevel(session.outsideDimension());
            if (outsideLevel != null && !VaultDimension.isVaultLevel(outsideLevel)) {
                PortalWindow window = session.outsideWindow();
                Vec3 exit = window.pointAt(window.width() * 0.5, 0.0).add(window.normal());
                return new Exit(outsideLevel, exit);
            }
        }
        Exit remembered = rememberedExit(server, record);
        if (remembered != null) {
            return remembered;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        return new Exit(overworld, new Vec3(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5));
    }

    private static @Nullable Exit rememberedExit(MinecraftServer server, @Nullable VaultRecord record) {
        VaultRecord.Door door = record == null ? null : record.lastDoor();
        if (door == null) {
            return null;
        }
        ServerLevel level = server.getLevel(door.dimension());
        if (level == null || VaultDimension.isVaultLevel(level)) {
            return null;
        }
        level.getChunk(door.anchor().getX() >> 4, door.anchor().getZ() >> 4);
        PortalWindow window = VaultPortalPlacement.windowNear(
            level, door.anchor(), door.facing(), REOPEN_SEARCH_RADIUS, false);
        if (window == null) {
            return null;
        }
        return new Exit(level, window.pointAt(window.width() * 0.5, 0.0).add(window.normal()));
    }

    static void expel(ServerPlayer player, @Nullable VaultRecord hint) {
        Exit exit = exitFor(player, hint);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(exit.level(), exit.pos().x, exit.pos().y, exit.pos().z,
            player.getYRot(), player.getXRot());
        player.resetFallDistance();
    }

    public static void escape(ServerPlayer player) {
        if (!VaultDimension.isVaultLevel(player.serverLevel())) {
            player.displayClientMessage(Component.translatable("hexwright.vault.escape_outside"), true);
            return;
        }
        expel(player, null);
    }

    private static boolean mayLogOutInside(ServerPlayer player) {
        VaultRecord record = VaultRegistry.get(player.server).byPosition(player.position());
        return record != null && record.owner().equals(player.getUUID());
    }

    private static void depositAtEntrance(ServerPlayer player) {
        if (!VaultDimension.isVaultLevel(player.serverLevel()) || mayLogOutInside(player)) {
            return;
        }
        Exit exit = exitFor(player);
        try {
            player.teleportTo(exit.level(), exit.pos().x, exit.pos().y, exit.pos().z,
                player.getYRot(), player.getXRot());
        } catch (RuntimeException e) {
            Hexwright.LOGGER.warn("Could not deposit {} outside their vault on logout",
                player.getGameProfile().getName(), e);
        }
    }


    private static void ensureRoomIntact(ServerLevel vaultLevel, VaultRecord record) {
        BlockPos probe = VaultRooms.intactProbe(record);
        vaultLevel.getChunk(probe.getX() >> 4, probe.getZ() >> 4);
        if (!vaultLevel.getBlockState(probe).isAir()) {
            return;
        }
        Hexwright.LOGGER.warn("Vault {} room missing its floor; regenerating template", record.id());
        for (ChunkPos chunk : VaultRooms.roomChunks(record)) {
            vaultLevel.getChunk(chunk.x, chunk.z);
        }
        VaultRooms.generate(vaultLevel, record);
    }

    private static List<ChunkPos> outsideChunksFor(PortalWindow window, int radius) {
        Vec3 center = window.center();
        int cx = Mth.floor(center.x) >> 4;
        int cz = Mth.floor(center.z) >> 4;
        List<ChunkPos> chunks = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunks.add(new ChunkPos(cx + dx, cz + dz));
            }
        }
        chunks.sort(Comparator.comparingInt(chunk ->
            (chunk.x - cx) * (chunk.x - cx) + (chunk.z - cz) * (chunk.z - cz)));
        return chunks;
    }

    private static List<String> clearedFor(MinecraftServer server, int vaultId) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Integer.valueOf(vaultId).equals(VaultContainment.claimOf(player.getUUID()))) {
                names.add(player.getGameProfile().getName());
            }
        }
        return names;
    }

    public static List<String> debugSummary(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        lines.add("Active vault sessions: " + SESSIONS_BY_VAULT.size());
        for (VaultPortalSession session : SESSIONS_BY_VAULT.values()) {
            String openerName = Optional.ofNullable(
                    server.getPlayerList().getPlayer(session.opener()))
                .map(p -> p.getGameProfile().getName())
                .orElse(session.opener().toString());
            VaultRecord record = getVault(server, session.vaultId());
            lines.add("Vault " + session.vaultId()
                + (record == null ? "" : " ("
                    + (record.artifact() ? "ARTIFACT" : record.grade()) + " / "
                    + VaultRooms.layoutOf(record)
                    + (record.build().isEmpty() ? "" : ": " + record.build()) + ")"));
            lines.add("  State: " + session.state());
            lines.add("  Opened by: " + openerName);
            if (record != null) {
                lines.add("  Access: " + record.access()
                    + (record.allowed().isEmpty() ? "" : " " + record.allowed().values()));
            }
            lines.add("  Occupants: " + session.occupants().size()
                + " (cleared by containment: " + clearedFor(server, session.vaultId()) + ")");
            lines.add("  Outside viewers: " + session.vaultViewers().size());
            lines.add("  Inside viewers: " + session.outsideViewers().size());
            lines.add("  Remote chunks: vault=" + session.vaultChunks().size()
                + " (room + Sodium padding ring, cap " + MAX_REMOTE_CHUNKS_PER_VAULT_VIEW
                + ", ticket radius " + session.vaultTicketRadius() + ")");
            lines.add("  Outside: ticketed=" + session.outsideTicketChunks().size()
                + " watched=" + session.outsideChunks().size()
                + " (radii " + OUTSIDE_TICKET_RADIUS_CHUNKS + "/" + OUTSIDE_WATCH_RADIUS_CHUNKS + ")");
            lines.add("  Close requested: " + session.closeRequested());
            lines.add("  Grace ticks: " + session.graceTicks());
        }
        return lines;
    }
}
