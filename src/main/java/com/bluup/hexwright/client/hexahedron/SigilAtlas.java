package com.bluup.hexwright.client.hexahedron;

import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.utils.HexUtils;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

import java.util.List;

public final class SigilAtlas {

    public static final int TILE = 192;
    private static final float EXTENT = 1.0f;
    private static final float MAX_DIST = 0.3f;

    private SigilAtlas() {
    }

    public static DynamicTexture bake(List<HexPattern> patterns, int columns, int rows, float fit, float drop) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, columns * TILE, rows * TILE, false);
        for (int tile = 0; tile < patterns.size(); tile++) {
            bake(image, tile % columns * TILE, tile / columns * TILE, patterns.get(tile), fit, drop);
        }
        DynamicTexture atlas = new DynamicTexture(image);
        atlas.setFilter(true, false);
        return atlas;
    }

    private static void bake(NativeImage image, int left, int top, HexPattern pattern, float fit, float drop) {
        Vec2[] points = fitted(pattern, fit, drop);
        int segments = points.length - 1;
        float[] before = new float[segments];
        float total = 0.0f;
        for (int i = 0; i < segments; i++) {
            before[i] = total;
            total += length(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y);
        }
        for (int row = 0; row < TILE; row++) {
            float sy = -((row + 0.5f) / TILE * 2.0f - 1.0f) * EXTENT;
            for (int column = 0; column < TILE; column++) {
                float sx = ((column + 0.5f) / TILE * 2.0f - 1.0f) * EXTENT;
                float best = Float.MAX_VALUE;
                float along = 0.0f;
                for (int i = 0; i < segments; i++) {
                    Vec2 a = points[i];
                    float abx = points[i + 1].x - a.x;
                    float aby = points[i + 1].y - a.y;
                    float lengthSq = abx * abx + aby * aby;
                    float t = Mth.clamp(((sx - a.x) * abx + (sy - a.y) * aby) / lengthSq, 0.0f, 1.0f);
                    float d = length(sx - (a.x + t * abx), sy - (a.y + t * aby));
                    if (d < best) {
                        best = d;
                        along = (before[i] + t * (float) Math.sqrt(lengthSq)) / total;
                    }
                }
                float toStart = length(sx - points[0].x, sy - points[0].y);
                image.setPixelRGBA(left + column, top + row,
                    0xFF << 24 | channel(toStart / MAX_DIST) << 16 | channel(along) << 8 | channel(best / MAX_DIST));
            }
        }
    }

    private static Vec2[] fitted(HexPattern pattern, float fit, float drop) {
        List<HexCoord> positions = pattern.positions();
        Vec2[] points = new Vec2[positions.size()];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            Vec2 px = HexUtils.coordToPx(positions.get(i), 1.0f, Vec2.ZERO);
            points[i] = new Vec2(px.x, -px.y);
            minX = Math.min(minX, points[i].x);
            minY = Math.min(minY, points[i].y);
            maxX = Math.max(maxX, points[i].x);
            maxY = Math.max(maxY, points[i].y);
        }
        float scale = 2.0f * fit / Math.max(maxX - minX, maxY - minY);
        float midX = (minX + maxX) / 2.0f;
        float midY = (minY + maxY) / 2.0f;
        for (int i = 0; i < points.length; i++) {
            points[i] = new Vec2((points[i].x - midX) * scale, (points[i].y - midY) * scale + drop);
        }
        return points;
    }

    private static int channel(float value) {
        return Mth.floor(Mth.clamp(value, 0.0f, 1.0f) * 255.0f + 0.5f);
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }
}
