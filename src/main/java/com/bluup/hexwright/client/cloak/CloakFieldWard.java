package com.bluup.hexwright.client.cloak;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.client.render.RenderLib;
import at.petrak.hexcasting.common.lib.hex.HexActions;
import com.bluup.hexwright.client.spellcasting.PatternDrawBatch;
import com.bluup.hexwright.server.cloak.CloakField;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class CloakFieldWard {

    private record Glyph(HexPattern pattern, List<Vec2> points, Set<Integer> dupIndices, float extent,
                         List<GridDot> grid) {
    }

    private record GridDot(Vec2 point, float closeness, int vertex) {
    }

    private record Placed(Glyph glyph, Vector3f point, Vector3f normal, Vector3f along,
                          float fit, long key, float life, float distanceSqr) {
    }

    record Face(int index, Vector3f middle, Vector3f normal, Vector3f across, Vector3f up, float width,
                float height) {

        Vector3f corner(float acrossShare, float upShare) {
            return new Vector3f(middle)
                .fma(acrossShare * width * 0.5f, across)
                .fma(upShare * height * 0.5f, up);
        }

        AABB bounds() {
            Vector3f min = corner(-1.0f, -1.0f).min(corner(1.0f, 1.0f));
            Vector3f max = corner(-1.0f, -1.0f).max(corner(1.0f, 1.0f));
            return new AABB(min.x, min.y, min.z, max.x, max.y, max.z);
        }
    }

    private static final float HEX_SIZE = 32.0f;

    private static final List<Glyph> GLYPHS = Stream.of(
            HexActions.GET_CASTER, HexActions.ENTITY_POS$EYE, HexActions.RAYCAST_ENTITY,
            HexActions.ADD_MOTION, HexActions.BLINK, HexActions.EXTINGUISH,
            HexActions.CONJURE_BLOCK, HexActions.CONJURE_LIGHT,
            HexActions.POTION$ABSORPTION, HexActions.POTION$REGENERATION,
            HexActions.POTION$WEAKNESS, HexActions.POTION$SLOWNESS,
            HexActions.SENTINEL$CREATE, HexActions.SENTINEL$GET_POS)
        .map(ActionRegistryEntry::prototype)
        .map(CloakFieldWard::glyphOf)
        .toList();

    private static final float PATCH = 5.0f;
    private static final float STEP = 0.8f;
    private static final float SECONDS = 6.0f;
    private static final float BUSY = 0.75f;
    private static final float DRAW = 0.45f;
    private static final float FLASH = 0.12f;
    private static final int MAX_GLYPHS = 24;
    private static final int MAX_GLYPHS_FAST = 12;

    private static final int INK = 0xFFAD6BF5;
    private static final int INK_END = 0xFFF2D6FF;
    private static final int FLASH_INK = 0xFFFFFFFF;

    private static final float FLOW_IRREGULAR = 0.1f;
    private static final float READABILITY_OFFSET = 0.2f;

    private static final int GRID_REACH = 2;
    private static final float GRID_DOT_RADIUS = 3.0f;
    private static final float GRID_DOT_RADIUS_FAR = 1.5f;
    private static final float GRID_ALPHA = 0.8f;
    private static final float GRID_FADE_IN = 0.06f;
    private static final int GRID_INK = 0xFFDBC7F5;

    private static final List<Placed> PLACED = new ArrayList<>();

    private CloakFieldWard() {
    }

    static void draw(Matrix4f pose, CloakField field, List<Face> faces, @Nullable Frustum frustum, Vec3 cameraPos,
                     float strength, boolean inside, boolean fast, double seconds) {
        if (strength <= 0.0f) {
            return;
        }
        long salt = field.id().getLeastSignificantBits();
        Vector3f eye = new Vector3f((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);

        PLACED.clear();
        for (Face face : faces) {
            int f = face.index();
            int columns = Math.max(1, Mth.floor(face.width() / PATCH));
            int rows = Math.max(1, Mth.floor(face.height() / PATCH));
            float cellWidth = face.width() / columns;
            float cellHeight = face.height() / rows;
            float fit = 0.4f * Math.min(cellWidth, cellHeight);
            float reach = 1.5f * fit + (GRID_REACH + 1) * STEP;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    long patch = salt ^ f * 0xD6E8FEB86659FD93L ^ row * 0x9E3779B97F4A7C15L ^ column * 0xC2B2AE3D27D4EB4FL;
                    float lifetime = SECONDS * Mth.lerp(random(patch, 1), 0.8f, 1.3f);
                    double cycle = seconds / lifetime + random(patch, 2);
                    long epoch = (long) Math.floor(cycle);
                    long key = patch + epoch * 0x632BE59BD9B4E019L;
                    if (random(key, 3) > BUSY) {
                        continue;
                    }

                    float x = (column + 0.35f + 0.3f * random(key, 4)) * cellWidth - face.width() * 0.5f;
                    float y = (row + 0.35f + 0.3f * random(key, 5)) * cellHeight - face.height() * 0.5f;
                    Vector3f point = new Vector3f(face.middle())
                        .fma(x, face.across())
                        .fma(y, face.up());
                    if (frustum != null && !frustum.isVisible(new AABB(
                        point.x - reach, point.y - reach, point.z - reach,
                        point.x + reach, point.y + reach, point.z + reach))) {
                        continue;
                    }
                    Vector3f toEye = eye.sub(point, new Vector3f());

                    Glyph glyph = GLYPHS.get((int) (random(key, 6) * GLYPHS.size()));
                    PLACED.add(new Placed(glyph, point, face.normal(), face.across(), fit, key,
                        (float) (cycle - epoch), toEye.lengthSquared()));
                }
            }
        }
        PLACED.sort(Comparator.comparingDouble(Placed::distanceSqr));

        int count = Math.min(PLACED.size(), fast ? MAX_GLYPHS_FAST : MAX_GLYPHS);
        for (int i = 0; i < count; i++) {
            writeGrid(pose, PLACED.get(i), strength, inside);
        }
        for (int i = 0; i < count; i++) {
            write(pose, PLACED.get(i), strength, inside);
        }
        PLACED.clear();
        PatternDrawBatch.flush();
    }

    static List<Face> facesOf(CloakField field) {
        Vec3 c = field.center();
        float half = field.halfWidth();
        float height = field.height();
        Vector3f base = new Vector3f((float) c.x, (float) c.y + height * 0.5f, (float) c.z);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        List<Face> faces = new ArrayList<>(5);
        for (int side = 0; side < 4; side++) {
            float angle = side * Mth.HALF_PI;
            Vector3f normal = new Vector3f(Mth.cos(angle), 0.0f, Mth.sin(angle));
            Vector3f across = new Vector3f(-normal.z, 0.0f, normal.x);
            faces.add(new Face(side, new Vector3f(normal).mul(half).add(base), normal, across, up,
                field.width(), height));
        }
        faces.add(new Face(4, new Vector3f((float) c.x, (float) c.y + height, (float) c.z), up,
            new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), field.width(), field.width()));
        return faces;
    }

    private static void writeGrid(Matrix4f pose, Placed placed, float strength, boolean inside) {
        float alpha = alphaOf(placed, strength) * GRID_ALPHA * smoothstep(0.0f, GRID_FADE_IN, placed.life());
        if (alpha <= 0.01f) {
            return;
        }

        int reached = reachedPoints(placed);
        Matrix4f mat = frame(pose, placed, inside);
        for (GridDot dot : placed.glyph().grid()) {
            if (dot.vertex() >= 0 && dot.vertex() < reached) {
                continue;
            }
            float radius = Mth.lerp(dot.closeness(), GRID_DOT_RADIUS_FAR, GRID_DOT_RADIUS);
            PatternDrawBatch.drawRoundSpot(mat, dot.point(), radius, ink(GRID_INK, 0.0f, alpha * dot.closeness()));
        }
    }

    private static void write(Matrix4f pose, Placed placed, float strength, boolean inside) {
        Glyph glyph = placed.glyph();
        float life = placed.life();
        float cast = life - DRAW;
        float flash = cast > 0.0f ? 1.0f - smoothstep(0.0f, FLASH, cast) : 0.0f;
        float alpha = alphaOf(placed, strength);
        if (alpha <= 0.01f) {
            return;
        }

        List<Vec2> points = glyph.points();
        int strokes = points.size() - 1;
        float drawn = Math.min(life / DRAW, 1.0f) * strokes;
        int whole = Mth.floor(drawn);
        boolean finished = whole >= strokes;
        List<Vec2> shown = finished ? points : points.subList(0, whole + 2);
        float lastSegment = finished ? 1.0f : Math.max(drawn - whole, 0.05f);

        PatternDrawBatch.drawPattern(frame(pose, placed, inside), shown, glyph.dupIndices(), finished,
            ink(INK, flash, alpha), ink(INK_END, flash, alpha),
            FLOW_IRREGULAR, READABILITY_OFFSET, lastSegment, placed.key());
    }

    private static float alphaOf(Placed placed, float strength) {
        float life = placed.life();
        float fade = 1.0f - smoothstep(DRAW + FLASH, 1.0f, life);
        return strength * fade * (life > DRAW ? 1.0f : 0.8f);
    }

    private static int reachedPoints(Placed placed) {
        int strokes = placed.glyph().points().size() - 1;
        float drawn = Math.min(placed.life() / DRAW, 1.0f) * strokes;
        return Math.min(Mth.floor(drawn) + 1, strokes + 1);
    }

    private static Matrix4f frame(Matrix4f pose, Placed placed, boolean inside) {
        float side = inside ? -1.0f : 1.0f;
        Vector3f normal = placed.normal();
        Vector3f round = placed.along();
        Vector3f up = round.cross(normal, new Vector3f());
        float blocksPerStep = Math.min(STEP, placed.fit() / placed.glyph().extent());
        return new Matrix4f(pose)
            .translate(placed.point())
            .mul(new Matrix4f(
                -side * round.x, -side * round.y, -side * round.z, 0.0f,
                -up.x, -up.y, -up.z, 0.0f,
                side * normal.x, side * normal.y, side * normal.z, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f))
            .rotateZ(Mth.floor(random(placed.key(), 7) * 6.0f) * Mth.PI / 3.0f)
            .scale(blocksPerStep / HEX_SIZE);
    }

    private static Glyph glyphOf(HexPattern pattern) {
        Vec2 centre = pattern.getCenter(HEX_SIZE);
        Vec2 offset = new Vec2(-centre.x, -centre.y);
        List<Vec2> points = pattern.toLines(HEX_SIZE, offset);
        float extent = 0.5f;
        for (Vec2 point : points) {
            extent = Math.max(extent, Math.max(Math.abs(point.x), Math.abs(point.y)) / HEX_SIZE);
        }
        List<HexCoord> positions = pattern.positions();
        return new Glyph(pattern, points, RenderLib.findDupIndices(positions), extent,
            gridAround(positions, offset));
    }

    private static List<GridDot> gridAround(List<HexCoord> positions, Vec2 offset) {
        Map<HexCoord, Integer> distances = new HashMap<>();
        for (HexCoord position : positions) {
            for (Iterator<HexCoord> it = position.rangeAround(GRID_REACH); it.hasNext(); ) {
                HexCoord coord = it.next();
                distances.merge(coord, coord.distanceTo(position), Math::min);
            }
        }
        List<GridDot> grid = new ArrayList<>(distances.size());
        distances.forEach((coord, distance) -> grid.add(new GridDot(
            HexUtils.coordToPx(coord, HEX_SIZE, offset),
            1.0f - distance / (GRID_REACH + 1.0f),
            positions.indexOf(coord))));
        return grid;
    }

    private static int ink(int color, float flash, float alpha) {
        return FastColor.ARGB32.color(
            Mth.floor(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f),
            Mth.floor(Mth.lerp(flash, FastColor.ARGB32.red(color), FastColor.ARGB32.red(FLASH_INK))),
            Mth.floor(Mth.lerp(flash, FastColor.ARGB32.green(color), FastColor.ARGB32.green(FLASH_INK))),
            Mth.floor(Mth.lerp(flash, FastColor.ARGB32.blue(color), FastColor.ARGB32.blue(FLASH_INK))));
    }

    private static float smoothstep(float from, float to, float x) {
        float t = Mth.clamp((x - from) / (to - from), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float random(long key, int salt) {
        long h = key + salt * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 40) / (float) (1L << 24);
    }
}
