package com.bluup.hexwright.server.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonPropCollision {

    private static final String TABLE = "/data/hexwright/dungeon_prop_collision.json";

    private static final Map<DungeonProp, Measured> TABLE_ROWS = load();

    private record Measured(int[] bounds, List<int[]> boxes) {
    }

    private static final Map<Placement, Map<BlockPos, VoxelShape>> CELLS = new ConcurrentHashMap<>();

    public record Placement(DungeonProp prop, Direction facing, float turn, float x, float y, float z) {

        public static Placement plain(DungeonProp prop, Direction facing) {
            return new Placement(prop, facing, 0, 0, 0, 0);
        }

        public static Placement byHand(DungeonProp prop, Direction facing) {
            Measured measured = TABLE_ROWS.get(prop);
            if (measured == null) {
                return plain(prop, facing);
            }
            int[] b = measured.bounds();
            int x = Math.floorDiv(16 - b[0] - b[3], 2);
            int z = prop.stance() == DungeonProp.Stance.WALL
                ? 16 - b[5]
                : Math.floorDiv(16 - b[2] - b[5], 2);
            return new Placement(prop, facing, 0, x / 16F, 0, z / 16F);
        }

        public Map<BlockPos, VoxelShape> cells() {
            return CELLS.computeIfAbsent(this, DungeonPropCollision::split);
        }

        public VoxelShape cell(BlockPos offset) {
            return cells().getOrDefault(offset, Shapes.empty());
        }
    }

    public static int[] bounds(DungeonProp prop) {
        Measured measured = TABLE_ROWS.get(prop);
        return measured == null ? new int[] {0, 0, 0, 16, 16, 16} : measured.bounds().clone();
    }

    private static Map<DungeonProp, Measured> load() {
        Map<DungeonProp, Measured> out = new EnumMap<>(DungeonProp.class);
        JsonObject table;
        try (InputStream in = DungeonPropCollision.class.getResourceAsStream(TABLE)) {
            if (in == null) {
                return out;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                table = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            return out;
        }
        for (DungeonProp prop : DungeonProp.values()) {
            JsonElement row = table.get(prop.propName());
            if (row == null) {
                continue;
            }
            List<int[]> boxes = new ArrayList<>();
            for (JsonElement box : row.getAsJsonObject().getAsJsonArray("boxes")) {
                boxes.add(sixInts(box.getAsJsonArray()));
            }
            out.put(prop, new Measured(sixInts(row.getAsJsonObject().getAsJsonArray("bounds")), boxes));
        }
        return out;
    }

    private static int[] sixInts(JsonArray row) {
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            out[i] = row.get(i).getAsInt();
        }
        return out;
    }

    private static Map<BlockPos, VoxelShape> split(Placement placement) {
        Map<BlockPos, VoxelShape> cells = new HashMap<>();
        Measured measured = TABLE_ROWS.get(placement.prop());
        for (int[] north : measured == null ? List.<int[]>of() : measured.boxes()) {
            int[] b = place(north, placement);
            for (int cx = Math.floorDiv(b[0], 16); cx <= Math.floorDiv(b[3] - 1, 16); cx++) {
                for (int cy = Math.floorDiv(b[1], 16); cy <= Math.floorDiv(b[4] - 1, 16); cy++) {
                    for (int cz = Math.floorDiv(b[2], 16); cz <= Math.floorDiv(b[5] - 1, 16); cz++) {
                        double x0 = Math.max(b[0], cx * 16) - cx * 16;
                        double y0 = Math.max(b[1], cy * 16) - cy * 16;
                        double z0 = Math.max(b[2], cz * 16) - cz * 16;
                        double x1 = Math.min(b[3], cx * 16 + 16) - cx * 16;
                        double y1 = Math.min(b[4], cy * 16 + 16) - cy * 16;
                        double z1 = Math.min(b[5], cz * 16 + 16) - cz * 16;
                        if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                            continue;
                        }
                        VoxelShape piece = Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16);
                        cells.merge(new BlockPos(cx, cy, cz), piece,
                            (a, c) -> Shapes.joinUnoptimized(a, c, BooleanOp.OR));
                    }
                }
            }
        }
        Map<BlockPos, VoxelShape> optimised = new LinkedHashMap<>();
        cells.forEach((offset, shape) -> optimised.put(offset, shape.optimize()));
        return Collections.unmodifiableMap(optimised);
    }

    private static int[] place(int[] b, Placement placement) {
        double turn = Math.toRadians(placement.turn());
        double facing = Math.toRadians(switch (placement.facing()) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> 270;
            default -> 0;
        });
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int x : new int[] {b[0], b[3]}) {
            for (int z : new int[] {b[2], b[5]}) {
                double[] p = rotateY(x - 8, z - 8, turn);
                p = rotateY(p[0] + placement.x() * 16, p[1] + placement.z() * 16, facing);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        double dy = placement.y() * 16;
        return new int[] {
            snapDown(minX + 8), snapDown(b[1] + dy), snapDown(minZ + 8),
            snapUp(maxX + 8), snapUp(b[4] + dy), snapUp(maxZ + 8)};
    }

    private static double[] rotateY(double x, double z, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new double[] {x * cos + z * sin, -x * sin + z * cos};
    }

    private static int snapDown(double sixteenths) {
        return (int) Math.floor(sixteenths + 1e-3);
    }

    private static int snapUp(double sixteenths) {
        return (int) Math.ceil(sixteenths - 1e-3);
    }

    private DungeonPropCollision() {
    }
}
