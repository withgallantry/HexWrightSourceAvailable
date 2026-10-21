package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.dungeon.DungeonDimension;
import com.bluup.hexwright.server.dungeon.DungeonInstances;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class VaultManager {

    public static final int MAX_REMOTE_CHUNKS_PER_VAULT_VIEW = 64;

    public static final int OUTSIDE_WATCH_RADIUS_CHUNKS = Mth.clamp(
        Integer.getInteger("hexwright.vault.outsideRadius", 4), 1, 8);

    public static final int OUTSIDE_TICKET_RADIUS_CHUNKS = 2;

    public static final double PORTAL_KEEPALIVE_DISTANCE = 32.0;

    public static final int CLOSE_GRACE_TICKS = 100;

    private static final int REOPEN_SEARCH_RADIUS = 4;

    private static boolean debug() {
        return HexwrightDebug.on(HexwrightDebug.VAULT);
    }

    private static final TicketType<UUID> TICKET = TicketType.create("hexwright_vault", UUID::compareTo);

    private static final int VIEW_TICKET_RADIUS = 2;

    private record DoorKey(int vaultId, int frameId) {
    }

    private static final Map<DoorKey, VaultPortalSession> SESSIONS = new LinkedHashMap<>();
    private static final Map<UUID, String> OPENER_NAMES = new HashMap<>();

    private VaultManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(VaultManager::tick);
        VaultChunkStreamer.addSource(SESSIONS::values);
        PortalManager.addCrossingGuard(VaultAccess::mayCross);
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
            for (VaultPortalSession session : List.copyOf(SESSIONS.values())) {
                reconcileOccupants(server, session);
                for (UUID occupant : List.copyOf(session.occupants())) {
                    ServerPlayer player = server.getPlayerList().getPlayer(occupant);
                    if (player != null) {
                        depositAtEntrance(player);
                    }
                }
                finalizeClose(server, session, "server stopping");
            }
            OPENER_NAMES.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            VaultCommands.register(dispatcher));
        VaultSkies.register();
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

    public static @Nullable VaultPortalSession sessionFor(int vaultId, int frameId) {
        return SESSIONS.get(new DoorKey(vaultId, frameId));
    }

    public static List<VaultPortalSession> sessionsOf(int vaultId) {
        List<VaultPortalSession> doors = new ArrayList<>(1);
        for (VaultPortalSession session : SESSIONS.values()) {
            if (session.vaultId() == vaultId) {
                doors.add(session);
            }
        }
        doors.sort(Comparator.comparingInt(VaultPortalSession::frameId));
        return doors;
    }

    public static boolean hasOpenDoor(int vaultId) {
        for (VaultPortalSession session : SESSIONS.values()) {
            if (session.vaultId() == vaultId) {
                return true;
            }
        }
        return false;
    }

    public static List<VaultPortalSession> sessionsOpenedBy(UUID opener) {
        List<VaultPortalSession> doors = new ArrayList<>(1);
        for (VaultPortalSession session : SESSIONS.values()) {
            if (session.opener().equals(opener)) {
                doors.add(session);
            }
        }
        return doors;
    }

    static @Nullable VaultPortalSession nearestDoor(int vaultId, Vec3 position) {
        VaultPortalSession nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (VaultPortalSession session : SESSIONS.values()) {
            if (session.vaultId() != vaultId) {
                continue;
            }
            double distSq = session.vaultWindow().center().distanceToSqr(position);
            if (distSq < nearestDistSq) {
                nearest = session;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    public static @Nullable VaultPortalSession sessionByPair(UUID pairId) {
        for (VaultPortalSession session : SESSIONS.values()) {
            if (session.pairId().equals(pairId)) {
                return session;
            }
        }
        return null;
    }

    public static Collection<VaultPortalSession> activeSessions() {
        return SESSIONS.values();
    }

    public static boolean openVault(ServerPlayer player, int vaultId, int frameId, PortalWindow outsideWindow) {
        return openVault(player.server, player.serverLevel(), Opener.of(player), vaultId, frameId, outsideWindow);
    }

    public record Opener(UUID id, String name, Consumer<Component> tell) {
        public static Opener of(ServerPlayer player) {
            return new Opener(player.getUUID(), player.getGameProfile().getName(),
                message -> player.displayClientMessage(message, true));
        }
    }

    public static boolean openVault(MinecraftServer server, ServerLevel outsideLevel, Opener opener, int vaultId,
                                    int frameId, PortalWindow outsideWindow) {
        VaultRecord record = VaultRegistry.get(server).byId(vaultId);
        if (record == null) {
            opener.tell().accept(Component.translatable("hexwright.vault.unknown", vaultId));
            return false;
        }
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (vaultLevel == null) {
            opener.tell().accept(Component.translatable("hexwright.vault.no_dimension"));
            return false;
        }
        if (VaultDimension.isVaultLevel(outsideLevel)) {
            opener.tell().accept(Component.translatable("hexwright.vault.inside_vault"));
            return false;
        }
        if (DungeonDimension.isDungeonLevel(outsideLevel)
            && !DungeonInstances.allowsVaultDoor(outsideLevel, outsideWindow.center())) {
            opener.tell().accept(Component.translatable("hexwright.vault.dungeon_safe_room_only"));
            return false;
        }

        VaultPortalSession standing = sessionFor(vaultId, frameId);
        if (standing != null && standing.opener().equals(opener.id())) {
            requestClose(server, standing, opener.tell());
            return true;
        }
        if (standing != null && frameId != VaultRecord.MAIN_DOOR) {
            opener.tell().accept(Component.translatable("hexwright.vault.frame_held", openerName(server, standing)));
            return false;
        }
        PortalWindow vaultWindow = interiorWindow(server, vaultLevel, record, frameId, opener.tell());
        if (vaultWindow == null) {
            return false;
        }

        List<VaultPortalSession> elsewhere = new ArrayList<>();
        for (VaultPortalSession mine : sessionsOpenedBy(opener.id())) {
            if (mine.vaultId() != vaultId) {
                elsewhere.add(mine);
            }
        }
        if (standing != null && wouldStrand(server, List.of(standing))) {
            opener.tell().accept(Component.translatable("hexwright.vault.occupied", vaultId));
            return false;
        }
        if (!elsewhere.isEmpty() && wouldStrand(server, elsewhere)) {
            opener.tell().accept(Component.translatable("hexwright.vault.occupied_replace"));
            return false;
        }
        if (standing != null) {
            ServerPlayer previous = server.getPlayerList().getPlayer(standing.opener());
            if (previous != null && !previous.getUUID().equals(opener.id())) {
                previous.displayClientMessage(
                    Component.translatable("hexwright.vault.door_moved", vaultId), false);
            }
            finalizeClose(server, standing, "door moved by " + opener.name());
        }
        for (VaultPortalSession mine : elsewhere) {
            finalizeClose(server, mine, "replaced by vault " + vaultId);
        }

        ensureRoomIntact(vaultLevel, record);
        OPENER_NAMES.put(opener.id(), opener.name());
        openSession(server, vaultLevel, record, frameId, vaultWindow, opener.id(), outsideLevel, outsideWindow);
        opener.tell().accept(Component.translatable("hexwright.vault.opened", vaultId));
        return true;
    }

    private static @Nullable PortalWindow interiorWindow(MinecraftServer server, ServerLevel vaultLevel,
                                                        VaultRecord record, int frameId,
                                                        Consumer<Component> tell) {
        if (frameId == VaultRecord.MAIN_DOOR) {
            return mainDoorWindow(record);
        }
        VaultRecord.Frame frame = record.frame(frameId);
        if (frame != null && !VaultFrames.ringIntact(vaultLevel, frame)) {
            breakFrame(server, record, frameId);
            frame = null;
        }
        if (frame == null) {
            tell.accept(Component.translatable("hexwright.vault.frame_gone", record.id()));
            return null;
        }
        if (!VaultFrames.openingClear(vaultLevel, frame)) {
            tell.accept(Component.translatable("hexwright.vault.frame_blocked", record.id()));
            return null;
        }
        return frame.window();
    }

    private static PortalWindow mainDoorWindow(VaultRecord record) {
        VaultRecord.Frame ring = record.mainDoorFrame();
        return ring != null ? ring.window() : VaultRooms.vaultWindow(record);
    }

    static void hangMainDoor(MinecraftServer server, VaultRecord record, VaultRecord.Frame ring) {
        record.setMainDoorFrame(ring);
        VaultRegistry.get(server).setDirty();
        HexwrightDebug.log(HexwrightDebug.VAULT, "[vault] vault {} main door hung in a ring at {}", record.id(),
            ring.anchor().toShortString());
        VaultPortalSession session = sessionFor(record.id(), VaultRecord.MAIN_DOOR);
        ServerLevel vaultLevel = VaultDimension.level(server);
        ServerLevel outsideLevel = session == null ? null : server.getLevel(session.outsideDimension());
        if (session == null || vaultLevel == null || outsideLevel == null) {
            return;
        }
        VaultPortalSession moved = replacePair(server, vaultLevel, record, session,
            outsideLevel, session.outsideWindow(), ring.window());
        moved.setCloseRequested(session.closeRequested());
    }

    private static VaultPortalSession openSession(MinecraftServer server, ServerLevel vaultLevel,
                                                  VaultRecord record, int frameId, PortalWindow vaultWindow,
                                                  UUID opener,
                                                  ServerLevel outsideLevel, PortalWindow outsideWindow) {
        UUID sessionId = UUID.randomUUID();
        List<ChunkPos> outsideChunks = outsideChunksFor(outsideWindow, OUTSIDE_WATCH_RADIUS_CHUNKS);
        List<ChunkPos> outsideTicketChunks = outsideChunksFor(outsideWindow, OUTSIDE_TICKET_RADIUS_CHUNKS);
        ChunkPos vaultChunk = VaultRooms.roomChunk(record);
        int vaultTicketRadius = VaultRooms.roomTicketRadius(record);
        List<ChunkPos> vaultChunks = VaultRooms.viewChunks(record);

        vaultLevel.getChunkSource().addRegionTicket(TICKET, vaultChunk, vaultTicketRadius, sessionId);
        for (ChunkPos chunk : outsideTicketChunks) {
            outsideLevel.getChunkSource().addRegionTicket(TICKET, chunk, VIEW_TICKET_RADIUS, sessionId);
        }

        PortalPair pair = new PortalPair(UUID.randomUUID(), opener,
            outsideWindow, vaultWindow, outsideLevel.getGameTime(),
            outsideLevel.dimension(), VaultDimension.KEY, true);
        PortalManager.get(outsideLevel).addPair(outsideLevel, pair);
        PortalManager.get(vaultLevel).addPair(vaultLevel, pair);

        VaultPortalSession session = new VaultPortalSession(sessionId, record.id(), frameId, opener, pair.id(),
            outsideLevel.dimension(), outsideWindow, vaultWindow,
            outsideChunks, outsideTicketChunks, vaultChunk, vaultTicketRadius, vaultChunks,
            VaultRooms.roomBounds(record));
        SESSIONS.put(new DoorKey(record.id(), frameId), session);
        if (frameId == VaultRecord.MAIN_DOOR) {
            rememberDoor(server, record, outsideLevel, outsideWindow);
        }
        if (debug()) {
            Hexwright.LOGGER.info("[vault] session {} opened vault {} door {} for {}",
                session.sessionId(), record.id(), frameId, opener);
        }
        return session;
    }

    private static void rememberDoor(MinecraftServer server, VaultRecord record,
                                     ServerLevel outsideLevel, PortalWindow outsideWindow) {
        if (DungeonDimension.isDungeonLevel(outsideLevel)) {
            return;
        }
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
        HexwrightDebug.log(HexwrightDebug.VAULT, "Rebuilt vault {} ({}{})", record.id(), record.grade(),
            record.build().isEmpty() ? "" : "/" + record.build());
        return true;
    }

    public static boolean relocateDoor(MinecraftServer server, VaultRecord record, int frameId,
                                       PortalWindow newWindow, Opener opener) {
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (vaultLevel == null) {
            return false;
        }
        ServerLevel newOutsideLevel = server.overworld();
        VaultPortalSession session = sessionFor(record.id(), frameId);
        if (session == null) {
            PortalWindow vaultWindow = interiorWindow(server, vaultLevel, record, frameId, opener.tell());
            if (vaultWindow == null) {
                return false;
            }
            ensureRoomIntact(vaultLevel, record);
            OPENER_NAMES.put(opener.id(), opener.name());
            openSession(server, vaultLevel, record, frameId, vaultWindow,
                opener.id(), newOutsideLevel, newWindow);
            return true;
        }
        if (frameId != VaultRecord.MAIN_DOOR && !session.opener().equals(opener.id())) {
            opener.tell().accept(Component.translatable("hexwright.vault.frame_held", openerName(server, session)));
            return false;
        }

        VaultPortalSession relocated = replacePair(server, vaultLevel, record, session,
            newOutsideLevel, newWindow, session.vaultWindow());
        if (frameId == VaultRecord.MAIN_DOOR) {
            rememberDoor(server, record, newOutsideLevel, newWindow);
        }
        if (debug()) {
            Hexwright.LOGGER.info("[vault] session {} (vault {} door {}) relocated by pattern",
                relocated.sessionId(), record.id(), frameId);
        }
        return true;
    }

    private static VaultPortalSession replacePair(MinecraftServer server, ServerLevel vaultLevel, VaultRecord record,
                                                  VaultPortalSession session, ServerLevel newOutsideLevel,
                                                  PortalWindow newWindow, PortalWindow newVaultWindow) {
        UUID sessionId = UUID.randomUUID();
        List<ChunkPos> outsideChunks = outsideChunksFor(newWindow, OUTSIDE_WATCH_RADIUS_CHUNKS);
        List<ChunkPos> outsideTicketChunks = outsideChunksFor(newWindow, OUTSIDE_TICKET_RADIUS_CHUNKS);
        for (ChunkPos chunk : outsideTicketChunks) {
            newOutsideLevel.getChunkSource().addRegionTicket(TICKET, chunk, VIEW_TICKET_RADIUS, sessionId);
        }
        vaultLevel.getChunkSource().addRegionTicket(
            TICKET, session.vaultChunk(), session.vaultTicketRadius(), sessionId);

        ServerLevel oldOutsideLevel = server.getLevel(session.outsideDimension());
        if (oldOutsideLevel != null) {
            PortalManager.get(oldOutsideLevel).removePair(oldOutsideLevel, session.pairId());
            for (ChunkPos chunk : session.outsideTicketChunks()) {
                oldOutsideLevel.getChunkSource().removeRegionTicket(
                    TICKET, chunk, VIEW_TICKET_RADIUS, session.sessionId());
            }
        }
        PortalManager.get(vaultLevel).removePair(vaultLevel, session.pairId());
        vaultLevel.getChunkSource().removeRegionTicket(
            TICKET, session.vaultChunk(), session.vaultTicketRadius(), session.sessionId());

        VaultChunkStreamer.onDoorRelocated(server, session, oldOutsideLevel);

        PortalPair newPair = new PortalPair(UUID.randomUUID(), session.opener(),
            newWindow, newVaultWindow, newOutsideLevel.getGameTime(),
            newOutsideLevel.dimension(), VaultDimension.KEY, true);
        PortalManager.get(newOutsideLevel).addPair(newOutsideLevel, newPair);
        PortalManager.get(vaultLevel).addPair(vaultLevel, newPair);

        VaultPortalSession relocated = new VaultPortalSession(sessionId, record.id(), session.frameId(),
            session.opener(), newPair.id(),
            newOutsideLevel.dimension(), newWindow, newVaultWindow,
            outsideChunks, outsideTicketChunks,
            session.vaultChunk(), session.vaultTicketRadius(), session.vaultChunks(),
            session.roomBounds());
        SESSIONS.put(new DoorKey(record.id(), session.frameId()), relocated);
        return relocated;
    }

    public static void requestCloseVault(ServerPlayer player) {
        List<VaultPortalSession> mine = sessionsOpenedBy(player.getUUID());
        if (mine.isEmpty()) {
            player.displayClientMessage(Component.translatable("hexwright.vault.none_open"), true);
            return;
        }
        for (VaultPortalSession session : mine) {
            requestClose(player.server, session, Opener.of(player).tell());
        }
    }

    public static void requestCloseDoor(ServerPlayer player, VaultPortalSession session) {
        requestClose(player.server, session, Opener.of(player).tell());
    }

    private static void requestClose(MinecraftServer server, VaultPortalSession session, Consumer<Component> tell) {
        if (!wouldStrand(server, List.of(session))) {
            finalizeClose(server, session, "close requested");
            tell.accept(Component.translatable("hexwright.vault.closed", session.vaultId()));
            return;
        }
        session.setCloseRequested(true);
        session.setState(VaultPortalSession.State.CLOSE_PENDING_OCCUPANTS);
        tell.accept(Component.translatable("hexwright.vault.close_pending", session.vaultId()));
    }

    private static boolean wouldStrand(MinecraftServer server, Collection<VaultPortalSession> closing) {
        for (VaultPortalSession session : closing) {
            reconcileOccupants(server, session);
            if (strands(session, closing)) {
                return true;
            }
        }
        return false;
    }

    private static boolean strands(VaultPortalSession session, Collection<VaultPortalSession> closing) {
        if (session.occupants().isEmpty()) {
            return false;
        }
        for (VaultPortalSession other : sessionsOf(session.vaultId())) {
            if (!closing.contains(other) && !other.closeRequested()) {
                return false;
            }
        }
        return true;
    }

    public static void closeDoorsWithin(MinecraftServer server, ResourceKey<Level> dimension, AABB area,
                                        String reason) {
        for (VaultPortalSession session : List.copyOf(SESSIONS.values())) {
            if (!session.outsideDimension().equals(dimension) || !area.contains(session.outsideWindow().center())) {
                continue;
            }
            if (wouldStrand(server, List.of(session))) {
                session.setCloseRequested(true);
                session.setState(VaultPortalSession.State.CLOSE_PENDING_OCCUPANTS);
            } else {
                finalizeClose(server, session, reason);
            }
        }
    }

    static void breakFrame(MinecraftServer server, VaultRecord record, int frameId) {
        if (!record.removeFrame(frameId)) {
            return;
        }
        VaultRegistry.get(server).setDirty();
        HexwrightDebug.log(HexwrightDebug.VAULT, "[vault] vault {} lost frame {}", record.id(), frameId);
        VaultPortalSession session = sessionFor(record.id(), frameId);
        if (session == null) {
            return;
        }
        reconcileOccupants(server, session);
        boolean onlyDoor = sessionsOf(record.id()).size() == 1;
        boolean openerTold = false;
        for (UUID occupant : List.copyOf(session.occupants())) {
            ServerPlayer player = server.getPlayerList().getPlayer(occupant);
            if (player == null
                || !(onlyDoor || VaultContainment.cameThrough(occupant, record.id(), frameId))) {
                continue;
            }
            expel(player, record, session);
            player.displayClientMessage(Component.translatable("hexwright.vault.frame_collapsed"), false);
            openerTold |= occupant.equals(session.opener());
        }
        ServerPlayer opener = server.getPlayerList().getPlayer(session.opener());
        if (opener != null && !openerTold) {
            opener.displayClientMessage(
                Component.translatable("hexwright.vault.frame_collapsed_opener", record.id()), false);
        }
        finalizeClose(server, session, "frame broken");
    }


    private static void tick(MinecraftServer server) {
        VaultFrames.tick(server);
        for (VaultPortalSession session : List.copyOf(SESSIONS.values())) {
            tickSession(server, session);
        }
        VaultChunkStreamer.tick(server);
        VaultContainment.tick(server);
    }

    private static void tickSession(MinecraftServer server, VaultPortalSession session) {
        if (session.state() == VaultPortalSession.State.CLOSED) {
            return;
        }
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
            if (strands(session, List.of(session))) {
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
        SESSIONS.remove(new DoorKey(session.vaultId(), session.frameId()), session);

        ServerLevel outsideLevel = server.getLevel(session.outsideDimension());
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (outsideLevel != null) {
            PortalManager.get(outsideLevel).removePair(outsideLevel, session.pairId());
            for (ChunkPos chunk : session.outsideTicketChunks()) {
                outsideLevel.getChunkSource().removeRegionTicket(
                    TICKET, chunk, VIEW_TICKET_RADIUS, session.sessionId());
            }
        }
        if (vaultLevel != null) {
            PortalManager.get(vaultLevel).removePair(vaultLevel, session.pairId());
            vaultLevel.getChunkSource().removeRegionTicket(
                TICKET, session.vaultChunk(), session.vaultTicketRadius(), session.sessionId());
        }
        VaultChunkStreamer.release(server, session);
        if (debug()) {
            Hexwright.LOGGER.info("[vault] session {} (vault {} door {}) closed: {}",
                session.sessionId(), session.vaultId(), session.frameId(), reason);
        }
    }

    private static void onDisconnect(ServerPlayer player) {
        depositAtEntrance(player);
        VaultChunkStreamer.forget(player.getUUID());
        VaultContainment.forget(player.getUUID());
        List<VaultPortalSession> mine = sessionsOpenedBy(player.getUUID());
        if (mine.isEmpty()) {
            return;
        }
        for (VaultPortalSession session : mine) {
            reconcileOccupants(player.server, session);
            session.occupants().remove(player.getUUID());
        }
        for (VaultPortalSession session : mine) {
            if (strands(session, List.of(session))) {
                session.setCloseRequested(true);
                session.setState(VaultPortalSession.State.CLOSE_PENDING_OCCUPANTS);
            } else {
                finalizeClose(player.server, session, "opener disconnected");
            }
        }
    }

    private static void onJoin(ServerPlayer player) {
        if (!VaultDimension.isVaultLevel(player.serverLevel())) {
            return;
        }
        MinecraftServer server = player.server;
        VaultRecord record = VaultRegistry.get(server).byPosition(player.position());
        if (record != null && hasOpenDoor(record.id())) {
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
        if (outsideLevel == null || VaultDimension.isVaultLevel(outsideLevel)
            || DungeonDimension.isDungeonLevel(outsideLevel)) {
            return false;
        }
        outsideLevel.getChunk(door.anchor().getX() >> 4, door.anchor().getZ() >> 4);
        PortalWindow window = VaultPortalPlacement.windowNear(
            outsideLevel, door.anchor(), door.facing(), REOPEN_SEARCH_RADIUS);
        if (window == null) {
            return false;
        }
        ensureRoomIntact(vaultLevel, record);
        openSession(server, vaultLevel, record, VaultRecord.MAIN_DOOR, mainDoorWindow(record),
            owner.getUUID(), outsideLevel, window);
        owner.displayClientMessage(
            Component.translatable("hexwright.vault.reopened", record.id()), true);
        return true;
    }


    private record Exit(ServerLevel level, Vec3 pos) {
    }

    private static Exit exitFor(ServerPlayer player) {
        return exitFor(player, null, null);
    }

    private static Exit exitFor(ServerPlayer player, @Nullable VaultRecord hint,
                                @Nullable VaultPortalSession through) {
        MinecraftServer server = player.server;
        VaultRecord record = hint != null
            ? hint
            : VaultRegistry.get(server).byPosition(player.position());
        VaultPortalSession session = through != null
            ? through
            : record == null ? null : exitDoor(player, record);
        Exit exit = session == null ? null : exitThrough(server, session);
        if (exit != null) {
            return exit;
        }
        Exit remembered = rememberedExit(server, record);
        if (remembered != null) {
            return remembered;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        return new Exit(overworld, new Vec3(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5));
    }

    private static @Nullable VaultPortalSession exitDoor(ServerPlayer player, VaultRecord record) {
        Integer cameThrough = VaultContainment.claimedDoor(player.getUUID(), record.id());
        VaultPortalSession door = cameThrough == null ? null : sessionFor(record.id(), cameThrough);
        if (door == null) {
            door = sessionFor(record.id(), VaultRecord.MAIN_DOOR);
        }
        if (door == null) {
            List<VaultPortalSession> open = sessionsOf(record.id());
            door = open.isEmpty() ? null : open.get(0);
        }
        return door;
    }

    private static @Nullable Exit exitThrough(MinecraftServer server, VaultPortalSession session) {
        ServerLevel outsideLevel = server.getLevel(session.outsideDimension());
        if (outsideLevel == null || VaultDimension.isVaultLevel(outsideLevel)) {
            return null;
        }
        PortalWindow window = session.outsideWindow();
        Vec3 exit = window.pointAt(window.width() * 0.5, 0.0).add(window.normal());
        return new Exit(outsideLevel, exit);
    }

    private static @Nullable Exit rememberedExit(MinecraftServer server, @Nullable VaultRecord record) {
        VaultRecord.Door door = record == null ? null : record.lastDoor();
        if (door == null) {
            return null;
        }
        ServerLevel level = server.getLevel(door.dimension());
        if (level == null || VaultDimension.isVaultLevel(level) || DungeonDimension.isDungeonLevel(level)) {
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
        expel(player, hint, null);
    }

    private static void expel(ServerPlayer player, @Nullable VaultRecord hint,
                              @Nullable VaultPortalSession through) {
        Exit exit = exitFor(player, hint, through);
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
        if (vaultLevel.getBlockState(probe).isAir()) {
            Hexwright.LOGGER.warn("Vault {} room missing its floor; regenerating template", record.id());
            for (ChunkPos chunk : VaultRooms.roomChunks(record)) {
                vaultLevel.getChunk(chunk.x, chunk.z);
            }
            VaultRooms.generate(vaultLevel, record);
        }
        if (VaultRooms.upgrade(vaultLevel, record)) {
            VaultRegistry.get(vaultLevel.getServer()).setDirty();
        }
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

    private static Component openerName(MinecraftServer server, VaultPortalSession session) {
        ServerPlayer opener = server.getPlayerList().getPlayer(session.opener());
        return opener != null ? opener.getDisplayName()
            : Component.literal(OPENER_NAMES.getOrDefault(session.opener(), session.opener().toString()));
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
        lines.add("Active vault sessions: " + SESSIONS.size());
        for (VaultPortalSession session : SESSIONS.values()) {
            String openerName = Optional.ofNullable(
                    server.getPlayerList().getPlayer(session.opener()))
                .map(p -> p.getGameProfile().getName())
                .orElse(session.opener().toString());
            VaultRecord record = getVault(server, session.vaultId());
            lines.add("Vault " + session.vaultId()
                + (session.frameId() == VaultRecord.MAIN_DOOR ? "" : ", frame " + session.frameId())
                + (record == null ? "" : " ("
                    + (record.artifact() ? "ARTIFACT" : record.grade()) + " / "
                    + VaultRooms.layoutOf(record)
                    + (record.build().isEmpty() ? "" : ": " + record.build()) + ")"));
            lines.add("  State: " + session.state());
            lines.add("  Opened by: " + openerName);
            if (record != null) {
                lines.add("  Access: " + record.access()
                    + (record.allowed().isEmpty() ? "" : " " + record.allowed().values()));
                lines.add("  Frames: " + record.frames().size() + "/" + VaultFrames.MAX_FRAMES
                    + (record.mainDoorFrame() == null ? ""
                        : ", main door in ring at " + record.mainDoorFrame().anchor().toShortString()));
            }
            lines.add("  Occupants: " + session.occupants().size()
                + " (cleared by containment: " + clearedFor(server, session.vaultId()) + ")");
            lines.add("  Outside viewers: " + session.insideViewers().size());
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
