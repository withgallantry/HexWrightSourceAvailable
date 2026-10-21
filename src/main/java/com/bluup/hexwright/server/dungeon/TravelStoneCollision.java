package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.DungeonPropCollision;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TravelStoneCollision {

    private static final String TABLE = "/data/hexwright/travel_stone_collision.json";

    private static final List<int[]> CLOSED;
    private static final List<int[]> OPEN;

    static {
        JsonObject table = load();
        CLOSED = boxes(table, "closed");
        OPEN = boxes(table, "open");
    }

    private static final Map<Direction, Map<BlockPos, VoxelShape>> CLOSED_CELLS = new ConcurrentHashMap<>();
    private static final Map<Direction, Map<BlockPos, VoxelShape>> OPEN_CELLS = new ConcurrentHashMap<>();
    private static final Map<Direction, Set<BlockPos>> REACH = new ConcurrentHashMap<>();

    private TravelStoneCollision() {
    }

    public static VoxelShape cell(Direction facing, TravelStoneBlockEntity.Phase phase, BlockPos offset) {
        Map<BlockPos, VoxelShape> cells = switch (phase) {
            case CLOSED -> closed(facing);
            case OPEN -> open(facing);
            case OPENING, CLOSING -> Map.of();
        };
        return cells.getOrDefault(offset, Shapes.empty());
    }

    public static Set<BlockPos> reach(Direction facing) {
        return REACH.computeIfAbsent(facing, f -> {
            Set<BlockPos> cells = new HashSet<>(closed(f).keySet());
            cells.addAll(open(f).keySet());
            return Collections.unmodifiableSet(cells);
        });
    }

    static boolean chunkReady(Level level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(
            SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) != null;
    }

    private static Map<BlockPos, VoxelShape> closed(Direction facing) {
        return CLOSED_CELLS.computeIfAbsent(facing, f -> DungeonPropCollision.cut(CLOSED, f));
    }

    private static Map<BlockPos, VoxelShape> open(Direction facing) {
        return OPEN_CELLS.computeIfAbsent(facing, f -> DungeonPropCollision.cut(OPEN, f));
    }

    private static JsonObject load() {
        try (InputStream in = TravelStoneCollision.class.getResourceAsStream(TABLE)) {
            if (in == null) {
                Hexwright.LOGGER.error("[travel stone] collision table {} is missing; stones will be walk-through", TABLE);
                return new JsonObject();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            Hexwright.LOGGER.error("[travel stone] could not read collision table {}", TABLE, e);
            return new JsonObject();
        }
    }

    private static List<int[]> boxes(JsonObject table, String pose) {
        List<int[]> out = new ArrayList<>();
        JsonArray rows = table.getAsJsonArray(pose);
        if (rows == null) {
            return out;
        }
        for (JsonElement row : rows) {
            JsonArray values = row.getAsJsonArray();
            int[] box = new int[6];
            for (int i = 0; i < 6; i++) {
                box[i] = values.get(i).getAsInt();
            }
            out.add(box);
        }
        return out;
    }
}
