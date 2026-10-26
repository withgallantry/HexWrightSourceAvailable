package com.bluup.hexwright.server.worldgen.travelstone;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TravelStoneRestorer {

    private static final int CHECK_INTERVAL = 200;

    private static final Map<ResourceKey<Level>, LongSet> PENDING = new HashMap<>();

    private static final Map<ResourceKey<Level>, Long2ObjectMap<List<TravelStonePiece>>> WATCHED = new HashMap<>();

    private TravelStoneRestorer() {
    }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(TravelStoneRestorer::noteChunk);
        ServerChunkEvents.CHUNK_UNLOAD.register(TravelStoneRestorer::forgetChunk);
        ServerTickEvents.END_SERVER_TICK.register(TravelStoneRestorer::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDING.clear();
            WATCHED.clear();
        });
    }

    private static boolean isTravelStone(Structure structure) {
        return structure.type() == HexwrightWorldgen.TRAVEL_STONE;
    }

    private static void noteChunk(ServerLevel level, LevelChunk chunk) {
        if (chunk.getAllReferences().keySet().stream().anyMatch(TravelStoneRestorer::isTravelStone)) {
            PENDING.computeIfAbsent(level.dimension(), key -> new LongOpenHashSet()).add(chunk.getPos().toLong());
        }
    }

    private static void forgetChunk(ServerLevel level, LevelChunk chunk) {
        long pos = chunk.getPos().toLong();
        LongSet pending = PENDING.get(level.dimension());
        if (pending != null) {
            pending.remove(pos);
        }
        Long2ObjectMap<List<TravelStonePiece>> watched = WATCHED.get(level.dimension());
        if (watched != null) {
            watched.remove(pos);
        }
    }

    private static void tick(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, LongSet> entry : PENDING.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            Long2ObjectMap<List<TravelStonePiece>> watched =
                WATCHED.computeIfAbsent(entry.getKey(), key -> new Long2ObjectOpenHashMap<>());
            for (long chunk : entry.getValue()) {
                List<TravelStonePiece> stones = stonesIn(level, new ChunkPos(chunk));
                if (!stones.isEmpty()) {
                    watched.put(chunk, stones);
                    stones.forEach(stone -> restore(level, stone));
                }
            }
        }
        PENDING.clear();
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, Long2ObjectMap<List<TravelStonePiece>>> entry : WATCHED.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level != null) {
                entry.getValue().values().forEach(stones -> stones.forEach(stone -> restore(level, stone)));
            }
        }
    }

    private static List<TravelStonePiece> stonesIn(ServerLevel level, ChunkPos chunk) {
        List<TravelStonePiece> stones = new ArrayList<>();
        for (StructureStart start : level.structureManager().startsForStructure(chunk, TravelStoneRestorer::isTravelStone)) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof TravelStonePiece stone && (in(chunk, stone.stonePos()) || in(chunk, stone.altarPos()))) {
                    stones.add(stone);
                }
            }
        }
        return stones;
    }

    private static boolean in(ChunkPos chunk, BlockPos pos) {
        return new ChunkPos(pos).equals(chunk);
    }

    private static void restore(ServerLevel level, TravelStonePiece stone) {
        if (stone.restore(level)) {
            HexwrightDebug.log(HexwrightDebug.DUNGEON, "[travel stone] put back the stone at {} in {}",
                stone.stonePos(), level.dimension().location());
        }
    }
}
