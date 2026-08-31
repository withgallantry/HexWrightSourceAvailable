package com.bluup.hexwright.client.spellcasting;

import at.petrak.hexcasting.client.render.RenderLib;
import at.petrak.hexcasting.client.render.VCDrawHelper;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class PatternDrawBatch implements VCDrawHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("hexwright-pattern-batch");

    private static final int ZAPPY_HOPS = 10;
    private static final float ZAPPY_VARIANCE = 2.5f;
    private static final float ZAPPY_SPEED = 0.1f;
    private static final float OUTER_WIDTH = 5f;
    private static final float INNER_WIDTH = 2f;
    private static final float OUTER_Z = 0f;
    private static final float INNER_Z = 1f;
    private static final float DOT_Z = 1f;
    private static final float DOT_RADIUS = 2f;

    private static final PatternDrawBatch INSTANCE = new PatternDrawBatch();

    private static boolean enabled = true;

    private final BufferBuilder builder = new BufferBuilder(4096);
    private boolean open;

    private float z;

    private @Nullable VertexFormat.Mode mode;
    private @Nullable Matrix4f matrix;
    private float groupZ;
    private int count;
    private float[] xs = new float[256];
    private float[] ys = new float[256];
    private float[] us = new float[256];
    private float[] vs = new float[256];
    private int[] colors = new int[256];

    private PatternDrawBatch() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        flush();
        enabled = value;
    }

    private static void ensureOpen() {
        if (INSTANCE.open) {
            return;
        }
        INSTANCE.builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_TEX);
        INSTANCE.open = true;
    }

    public static void flush() {
        if (!INSTANCE.open) {
            return;
        }
        INSTANCE.endGroup();
        INSTANCE.open = false;

        BufferBuilder.RenderedBuffer rendered = INSTANCE.builder.endOrDiscardIfEmpty();
        if (rendered == null) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, VCDrawHelper.getWHITE());
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferUploader.drawWithShader(rendered);
    }

    public static void drawSpot(Matrix4f mat, Vec2 point, float radius, float r, float g, float b, float a) {
        if (!enabled) {
            RenderLib.drawSpot(mat, point, radius, r, g, b, a);
            return;
        }

        ensureOpen();
        INSTANCE.z = DOT_Z;
        RenderLib.drawSpot(mat, point, radius, packColor(r, g, b, a), INSTANCE);
    }

    public static void drawPattern(Matrix4f mat, List<Vec2> points, Set<Integer> dupIndices, boolean drawLast,
                                   int colorStart, int colorEnd, float flowIrregular, float readabilityOffset,
                                   float lastSegmentLenProportion, double seed) {
        if (!enabled) {
            RenderLib.drawPatternFromPoints(mat, points, dupIndices, drawLast, colorStart, colorEnd,
                flowIrregular, readabilityOffset, lastSegmentLenProportion, seed);
            return;
        }

        ensureOpen();

        List<Vec2> zappy = RenderLib.makeZappy(points, dupIndices, ZAPPY_HOPS, ZAPPY_VARIANCE, ZAPPY_SPEED,
            flowIrregular, readabilityOffset, lastSegmentLenProportion, seed);

        INSTANCE.z = OUTER_Z;
        RenderLib.drawLineSeq(mat, zappy, OUTER_WIDTH, colorStart, colorEnd, INSTANCE);

        INSTANCE.z = INNER_Z;
        RenderLib.drawLineSeq(mat, zappy, INNER_WIDTH,
            RenderLib.screenCol(colorStart), RenderLib.screenCol(colorEnd), INSTANCE);

        float r = RenderLib.dodge(FastColor.ARGB32.red(colorEnd)) / 255f;
        float g = RenderLib.dodge(FastColor.ARGB32.green(colorEnd)) / 255f;
        float b = RenderLib.dodge(FastColor.ARGB32.blue(colorEnd)) / 255f;
        float a = FastColor.ARGB32.alpha(colorEnd) / 255f;

        int nodeCount = drawLast ? points.size() : points.size() - 1;
        for (int i = 0; i < nodeCount; i++) {
            drawSpot(mat, points.get(i), DOT_RADIUS, r, g, b, a);
        }
    }

    private static int packColor(float r, float g, float b, float a) {
        return FastColor.ARGB32.color((int) (a * 255f), (int) (r * 255f), (int) (g * 255f), (int) (b * 255f));
    }


    @Override
    public VertexConsumer vcSetupAndSupply(VertexFormat.Mode vertMode) {
        endGroup();
        this.mode = vertMode;
        this.matrix = null;
        this.groupZ = this.z;
        this.count = 0;
        return builder;
    }

    @Override
    public void vertex(VertexConsumer vc, int color, Vec2 pos, Vec2 uv, Matrix4f matrix) {
        ensureCapacity(count + 1);
        xs[count] = pos.x;
        ys[count] = pos.y;
        us[count] = uv.x;
        vs[count] = uv.y;
        colors[count] = color;
        count++;
        this.matrix = matrix;
    }

    @Override
    public void vcEndDrawer(VertexConsumer vc) {
        endGroup();
    }

    private void endGroup() {
        VertexFormat.Mode groupMode = this.mode;
        this.mode = null;
        if (groupMode == null || count == 0 || !open) {
            count = 0;
            return;
        }

        switch (groupMode) {
            case TRIANGLES -> {
                for (int i = 0; i + 2 < count; i += 3) {
                    triangle(i, i + 1, i + 2);
                }
            }
            case TRIANGLE_STRIP -> {
                for (int i = 2; i < count; i++) {
                    triangle(i - 2, i - 1, i);
                }
            }
            case TRIANGLE_FAN -> {
                for (int i = 2; i < count; i++) {
                    triangle(0, i - 1, i);
                }
            }
            case QUADS -> {
                for (int i = 0; i + 3 < count; i += 4) {
                    triangle(i, i + 1, i + 2);
                    triangle(i, i + 2, i + 3);
                }
            }
            default -> LOGGER.warn(
                "Dropped a spellcasting grid primitive drawn as {}, which cannot be batched as triangles",
                groupMode);
        }

        count = 0;
    }

    private void triangle(int a, int b, int c) {
        emit(a);
        emit(b);
        emit(c);
    }

    private void emit(int i) {
        builder.vertex(matrix, xs[i], ys[i], groupZ)
            .color(colors[i])
            .uv(us[i], vs[i])
            .endVertex();
    }

    private void ensureCapacity(int needed) {
        if (needed <= xs.length) {
            return;
        }
        int size = Math.max(needed, xs.length * 2);
        xs = Arrays.copyOf(xs, size);
        ys = Arrays.copyOf(ys, size);
        us = Arrays.copyOf(us, size);
        vs = Arrays.copyOf(vs, size);
        colors = Arrays.copyOf(colors, size);
    }
}
