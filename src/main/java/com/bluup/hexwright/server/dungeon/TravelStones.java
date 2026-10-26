package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.portal.PortalManager;
import com.bluup.hexwright.server.portal.PortalPair;
import com.bluup.hexwright.server.portal.PortalWindow;
import com.bluup.hexwright.server.vault.VaultChunkStreamer;
import com.bluup.hexwright.server.vault.VaultManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TravelStones {

    public static final double WINDOW_DIAMETER = 5.0;

    public static final double WINDOW_SILL = 0.25;

    static final int LANDING_DISTANCE = 4;

    private static final int ALTAR_REACH = 16;
    private static final int ALTAR_REACH_UP = 6;

    private static final int STONE_SIDE = 0;

    private static final TicketType<Integer> TICKET = TicketType.create("hexwright_travel_stone", Integer::compareTo);
    private static final int OUTSIDE_TICKET_RADIUS = 3;
    private static final int ROOM_TICKET_RADIUS = 2;

    private static final Map<Integer, TravelStoneWindow> WINDOWS = new HashMap<>();

    private TravelStones() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TravelStones::tick);
        VaultChunkStreamer.addSource(WINDOWS::values);
        PortalManager.addCrossingGuard(TravelStones::mayCross);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (TravelStoneWindow window : List.copyOf(WINDOWS.values())) {
                close(server, window, "server stopping");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WINDOWS.clear());
    }


    public static Vec3 windowCenter(BlockPos stone, double sill) {
        return new Vec3(stone.getX() + 0.5, stone.getY() + sill + WINDOW_DIAMETER / 2.0, stone.getZ() + 0.5);
    }

    public static PortalWindow stoneWindow(Vec3 center, Direction front) {
        Direction across = front.getCounterClockWise();
        Vec3 u = new Vec3(across.getStepX(), 0.0, across.getStepZ()).scale(WINDOW_DIAMETER);
        Vec3 v = new Vec3(0.0, WINDOW_DIAMETER, 0.0);
        Vec3 origin = center.subtract(u.scale(0.5)).subtract(v.scale(0.5));
        return new PortalWindow(origin, u, v, true);
    }


    public static InteractionResult useKey(ServerLevel level, BlockPos altar, TravelStoneBlockEntity stone,
                                           ServerPlayer player, ItemStack key, DungeonInstance.Kind kind) {
        if (altarBusy(level, altar) || stone.isOpen(level.getServer())) {
            player.displayClientMessage(Component.translatable("message.hexwright.travel_stone.busy"), true);
            return InteractionResult.FAIL;
        }
        DungeonInstance instance = DungeonInstances.openAtStone(level, stone, player.getUUID(), kind,
            message -> player.displayClientMessage(message, true));
        if (instance == null) {
            return InteractionResult.FAIL;
        }
        stone.bind(instance.id());
        if (level.getBlockEntity(altar) instanceof CrystalAltarBlockEntity entity) {
            entity.bind(instance);
        }
        if (!player.getAbilities().instabuild) {
            key.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.hexwright.dungeon.stone_forming")
            .withStyle(ChatFormatting.DARK_PURPLE), true);
        return InteractionResult.CONSUME;
    }

    public static InteractionResult takeKey(ServerLevel level, CrystalAltarBlockEntity altar, ServerPlayer player) {
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(level.getServer());
        DungeonInstance instance = registry.byId(altar.instanceId());
        if (instance == null) {
            return InteractionResult.PASS;
        }
        if (!instance.opener().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.hexwright.travel_stone.not_yours"), true);
            return InteractionResult.FAIL;
        }
        long now = level.getServer().overworld().getGameTime();
        if (instance.windowForming(now)) {
            player.displayClientMessage(Component.translatable("message.hexwright.travel_stone.still_opening"), true);
            return InteractionResult.FAIL;
        }
        instance.closeRift(now);
        registry.setDirty();
        altar.shatterKey(level);
        player.displayClientMessage(Component.translatable("message.hexwright.travel_stone.key_taken")
            .withStyle(ChatFormatting.DARK_PURPLE), true);
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] {} took the key back from instance {}",
            player.getGameProfile().getName(), instance.id());
        return InteractionResult.CONSUME;
    }

    private static boolean altarBusy(ServerLevel level, BlockPos altar) {
        return level.getBlockEntity(altar) instanceof CrystalAltarBlockEntity entity && entity.lit();
    }

    static @Nullable TravelStoneBlockEntity nearestStone(ServerLevel level, BlockPos altar) {
        TravelStoneBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        int minChunkX = (altar.getX() - ALTAR_REACH) >> 4;
        int maxChunkX = (altar.getX() + ALTAR_REACH) >> 4;
        int minChunkZ = (altar.getZ() - ALTAR_REACH) >> 4;
        int maxChunkZ = (altar.getZ() + ALTAR_REACH) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (BlockEntity entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (entity.getType() != InstancedDungeons.TRAVEL_STONE_BLOCK_ENTITY
                        || !(entity instanceof TravelStoneBlockEntity stone)) {
                        continue;
                    }
                    BlockPos pos = stone.getBlockPos();
                    if (Math.abs(pos.getX() - altar.getX()) > ALTAR_REACH || Math.abs(pos.getZ() - altar.getZ()) > ALTAR_REACH
                        || Math.abs(pos.getY() - altar.getY()) > ALTAR_REACH_UP) {
                        continue;
                    }
                    double distance = pos.distSqr(altar);
                    if (distance < best) {
                        best = distance;
                        nearest = stone;
                    }
                }
            }
        }
        return nearest;
    }

    static TravelStoneBlockEntity.Phase phaseFor(MinecraftServer server, int instanceId) {
        DungeonInstance instance = instanceId < 0 ? null : DungeonInstanceRegistry.get(server).byId(instanceId);
        if (instance == null) {
            return TravelStoneBlockEntity.Phase.CLOSED;
        }
        long now = server.overworld().getGameTime();
        if (instance.windowForming(now)) {
            return TravelStoneBlockEntity.Phase.OPENING;
        }
        return instance.windowHolds(now)
            ? TravelStoneBlockEntity.Phase.OPEN
            : TravelStoneBlockEntity.Phase.CLOSED;
    }


    private static void tick(MinecraftServer server) {
        ServerLevel dungeons = DungeonDimension.level(server);
        if (dungeons == null) {
            return;
        }
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(server);
        if (registry.size() == 0 && WINDOWS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (TravelStoneWindow window : List.copyOf(WINDOWS.values())) {
            DungeonInstance instance = registry.byId(window.instanceId());
            if (instance == null || !instance.windowHolds(now)) {
                close(server, window, "its time is up");
            }
        }
        for (DungeonInstance instance : registry.all()) {
            if (instance.windowHolds(now) && !WINDOWS.containsKey(instance.id())) {
                open(server, dungeons, instance);
            }
        }
        for (TravelStoneWindow window : WINDOWS.values()) {
            window.occupants().clear();
            for (ServerPlayer player : dungeons.players()) {
                DungeonInstance standing = DungeonInstances.at(server, player.position());
                if (standing != null && standing.id() == window.instanceId()) {
                    window.occupants().add(player.getUUID());
                }
            }
        }
    }

    private static void open(MinecraftServer server, ServerLevel dungeons, DungeonInstance instance) {
        DungeonInstance.Entrance entrance = instance.entrance();
        ServerLevel outside = server.getLevel(entrance.dimension());
        if (outside == null || entrance.stone() == null) {
            return;
        }
        PortalWindow stoneWindow = stoneWindow(entrance.center(), entrance.facing().getOpposite());
        DungeonSafeRoom room = instance.safeRoom();
        PortalWindow roomWindow = room.window(entrance.center().y - entrance.stone().getY() - WINDOW_DIAMETER / 2.0);
        ChunkPos stoneChunk = new ChunkPos(entrance.stone());
        BoundingBox box = room.box();
        ChunkPos roomChunk = new ChunkPos(box.getCenter());

        outside.getChunkSource().addRegionTicket(TICKET, stoneChunk, OUTSIDE_TICKET_RADIUS, instance.id());
        dungeons.getChunkSource().addRegionTicket(TICKET, roomChunk, ROOM_TICKET_RADIUS, instance.id());
        PortalPair pair = new PortalPair(UUID.randomUUID(), instance.opener(), stoneWindow, roomWindow,
            outside.getGameTime(), entrance.dimension(), DungeonDimension.KEY, true);
        PortalManager.get(outside).addPair(outside, pair);
        PortalManager.get(dungeons).addPair(dungeons, pair);

        List<ChunkPos> roomChunks = chunksBetween(roomChunk, (box.minX() >> 4) - 1, (box.minZ() >> 4) - 1,
            (box.maxX() >> 4) + 1, (box.maxZ() >> 4) + 1);
        int watch = VaultManager.OUTSIDE_WATCH_RADIUS_CHUNKS;
        List<ChunkPos> outsideChunks = chunksBetween(stoneChunk, stoneChunk.x - watch, stoneChunk.z - watch,
            stoneChunk.x + watch, stoneChunk.z + watch);
        BlockPos altar = room.altar();
        if (!(dungeons.getBlockEntity(altar) instanceof CrystalAltarBlockEntity)) {
            dungeons.setBlock(altar, InstancedDungeons.CRYSTAL_ALTAR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (dungeons.getBlockEntity(altar) instanceof CrystalAltarBlockEntity inside && !inside.lit()) {
            inside.bind(instance);
        }
        WINDOWS.put(instance.id(), new TravelStoneWindow(instance.id(), pair.id(), entrance.dimension(), stoneWindow,
            outsideChunks, stoneChunk, roomChunk, roomChunks, room.interior()));
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] travel stone at {} open onto instance {}",
            entrance.stone().toShortString(), instance.id());
    }

    private static void close(MinecraftServer server, TravelStoneWindow window, String reason) {
        WINDOWS.remove(window.instanceId());
        ServerLevel outside = server.getLevel(window.outsideDimension());
        if (outside != null) {
            PortalManager.get(outside).removePair(outside, window.pairId());
            outside.getChunkSource().removeRegionTicket(TICKET, window.outsideTicketChunk(), OUTSIDE_TICKET_RADIUS,
                window.instanceId());
        }
        ServerLevel dungeons = DungeonDimension.level(server);
        if (dungeons != null) {
            PortalManager.get(dungeons).removePair(dungeons, window.pairId());
            dungeons.getChunkSource().removeRegionTicket(TICKET, window.roomTicketChunk(), ROOM_TICKET_RADIUS,
                window.instanceId());
        }
        VaultChunkStreamer.release(server, window);
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] travel stone window onto instance {} closed: {}",
            window.instanceId(), reason);
    }

    private static List<ChunkPos> chunksBetween(ChunkPos center, int minX, int minZ, int maxX, int maxZ) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        chunks.sort(Comparator.comparingInt(chunk -> chunk.getChessboardDistance(center)));
        return chunks;
    }

    private static boolean mayCross(PortalPair pair, int side, Entity entity) {
        if (side != STONE_SIDE || entity.getServer() == null) {
            return true;
        }
        TravelStoneWindow window = null;
        for (TravelStoneWindow candidate : WINDOWS.values()) {
            if (candidate.pairId().equals(pair.id())) {
                window = candidate;
                break;
            }
        }
        if (window == null) {
            return true;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        DungeonInstance instance = DungeonInstanceRegistry.get(player.server).byId(window.instanceId());
        return instance != null && !instance.departed().contains(player.getUUID());
    }
}
