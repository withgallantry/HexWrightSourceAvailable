package com.bluup.hexwright.client.flourish;

import com.bluup.hexwright.common.flourish.FlourishFigure;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.IntUnaryOperator;

public final class FlourishPen {

    public static final int SEAL = 0;
    public static final int LATTICE = 1;
    public static final int STROKE = 2;
    public static final int BEAM = 3;
    public static final int STAR = 4;
    public static final int RING = 5;
    public static final int ORBIT = 6;
    public static final int THREAD = 7;
    public static final int PETALS = 8;
    public static final int SHELL = 9;
    public static final int VORTEX = 10;
    public static final int BAND = 11;
    public static final int STREAK = 12;
    public static final int ARC = 13;
    public static final int EYE = 14;
    public static final int ECLIPSE = 15;
    public static final int SHARD = 16;
    public static final int WINGS = 17;
    public static final int RIPPLE = 18;
    public static final int GEARS = 19;
    public static final int FLAME = 20;
    public static final int FIGURE = 21;
    public static final int WEB = 22;
    public static final int FROST = 23;
    public static final int MOTH = 24;
    public static final int CONSTELLATION = 25;
    public static final int MAZE = 26;
    public static final int CHAINS = 27;
    public static final int BLADE = 28;
    public static final int TESSERACT = 29;
    public static final int SERPENT = 30;
    public static final int RIFT = 31;
    public static final int CLOCK = 32;
    public static final int SINGULARITY = 33;
    public static final int EFFIGY = 34;
    public static final int AURORA = 35;
    public static final int PRISM = 36;
    public static final int ROSE_WINDOW = 37;
    public static final int WILDFLOWER = 38;
    public static final int DANDELION = 39;
    public static final int MYCELIUM = 40;
    public static final int BRIAR = 41;
    public static final int WORLD_TREE = 42;

    public static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private static final double DISC_MARGIN = 1.12;

    private static final int BAND_FACES = 32;

    private static final String[] COLOUR_UNIFORMS = {"Color0", "Color1", "Color2", "Color3"};
    private static final String[] RING_UNIFORMS = {"Ring0", "Ring1", "Ring2", "Ring3", "Ring4", "Ring5"};

    private static final float[] RING_WEIGHTS = {0.014f, 0.022f, 0.034f, 0.05f};
    private static final float[] RING_SPINS = {0.0f, 0.3f, 0.7f, 1.4f};

    private final ShaderInstance shader;
    private final Matrix4f pose;
    private final Vec3 cameraPos;
    private final Vec3 cameraUp;
    private final Vec3 cameraRight;
    @Nullable
    private final Vec3 viewDirection;
    private final float partialTick;

    private float segmentX;
    private float segmentY = 1.0f;
    private float segmentZ;
    private float segmentW = 1.0f;

    private final int[] palette = new int[COLOUR_UNIFORMS.length];
    private int shade;

    FlourishPen(ShaderInstance shader, Matrix4f pose, Camera camera, float partialTick) {
        this(shader, pose, camera.getPosition(), up(camera), right(camera), null, partialTick);
    }

    FlourishPen(ShaderInstance shader, Matrix4f pose, Vec3 origin, Vec3 up, Vec3 right, @Nullable Vec3 viewDirection,
                float partialTick) {
        this.shader = shader;
        this.pose = pose;
        this.cameraPos = origin;
        this.cameraUp = up;
        this.cameraRight = right;
        this.viewDirection = viewDirection;
        this.partialTick = partialTick;
    }

    private static Vec3 up(Camera camera) {
        Vector3f up = camera.getUpVector();
        return new Vec3(up.x(), up.y(), up.z());
    }

    private static Vec3 right(Camera camera) {
        Vector3f left = camera.getLeftVector();
        return new Vec3(-left.x(), -left.y(), -left.z());
    }

    public Vec3 cameraPos() {
        return cameraPos;
    }

    public Vec3 cameraUp() {
        return cameraUp;
    }

    public Vec3 cameraRight() {
        return cameraRight;
    }

    public float partialTick() {
        return partialTick;
    }

    public void flourish(float age, float seed, float intensity) {
        shader.safeGetUniform("Age").set(age);
        shader.safeGetUniform("Seed").set(seed);
        shader.safeGetUniform("Intensity").set(intensity);
    }

    public void palette(IntUnaryOperator colour) {
        for (int i = 0; i < palette.length; i++) {
            palette[i] = colour.applyAsInt(i);
        }
        shade = 0;
        uploadPalette();
    }

    public void shade(int by) {
        int wrapped = Math.floorMod(by, palette.length);
        if (wrapped != shade) {
            shade = wrapped;
            uploadPalette();
        }
    }

    private void uploadPalette() {
        for (int i = 0; i < COLOUR_UNIFORMS.length; i++) {
            int rgb = palette[(i + shade) % palette.length];
            shader.safeGetUniform(COLOUR_UNIFORMS[i]).set(
                (rgb >> 16 & 0xFF) / 255.0f, (rgb >> 8 & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
        }
    }

    public FlourishPen segment(float x, float y, float z, float w) {
        segmentX = x;
        segmentY = y;
        segmentZ = z;
        segmentW = w;
        return this;
    }

    public FlourishPen figure(FlourishFigure figure) {
        for (int i = 0; i < RING_UNIFORMS.length; i++) {
            if (i >= figure.rings().size()) {
                shader.safeGetUniform(RING_UNIFORMS[i]).set(0.0f, 0.0f, 0.0f, 0.0f);
                continue;
            }
            FlourishFigure.Ring ring = figure.rings().get(i);
            int code = ring.shape().ordinal() | ring.count() << 4 | ring.form() << 8 | ring.turn() << 10 | ring.hue() << 12;
            float spin = Math.signum(ring.spin()) * RING_SPINS[Math.abs(ring.spin())];
            shader.safeGetUniform(RING_UNIFORMS[i]).set((float) code, ring.radius() / (float) FlourishFigure.RADII,
                RING_WEIGHTS[ring.weight()], spin);
        }
        return segment(figure.rings().size(), figure.entrance().ordinal(), figure.exit().ordinal(), 0.0f);
    }

    public void disc(int style, Vec3 centre, Vec3 normal, double radius, float progress) {
        Vec3 n = normal.normalize();
        Vec3 reference = Math.abs(n.y) < 0.99 ? UP : new Vec3(1.0, 0.0, 0.0);
        Vec3 u = n.cross(reference).normalize();
        Vec3 w = n.cross(u);
        double m = radius * DISC_MARGIN;
        uniforms(style, progress, (float) radius, (float) radius);
        quad(centre, u, w, -m, m, -m, m);
    }

    public void billboard(int style, Vec3 centre, double radius, float progress) {
        double m = radius * DISC_MARGIN;
        uniforms(style, progress, (float) radius, (float) radius);
        quad(centre, cameraRight, cameraUp, -m, m, -m, m);
    }

    public void band(int style, Vec3 centre, double radius, double halfHeight, float progress) {
        double circumference = Mth.TWO_PI * radius;
        double reach = halfHeight * 1.6;
        uniforms(style, progress, (float) circumference, (float) halfHeight);
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < BAND_FACES; i++) {
            double a0 = i * Mth.TWO_PI / BAND_FACES;
            double a1 = (i + 1) * Mth.TWO_PI / BAND_FACES;
            Vec3 p0 = centre.add(Math.cos(a0) * radius, 0.0, Math.sin(a0) * radius);
            Vec3 p1 = centre.add(Math.cos(a1) * radius, 0.0, Math.sin(a1) * radius);
            double s0 = circumference * i / BAND_FACES;
            double s1 = circumference * (i + 1) / BAND_FACES;
            bandVertex(builder, p0, -reach, s0);
            bandVertex(builder, p1, -reach, s1);
            bandVertex(builder, p1, reach, s1);
            bandVertex(builder, p0, reach, s0);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    public void axial(int style, Vec3 from, Vec3 to, double width, double halfBreadth, double margin,
                      float progress) {
        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 1e-4) {
            return;
        }
        Vec3 axis = along.scale(1.0 / length);
        Vec3 side = axis.cross(viewDirection != null ? viewDirection : from.add(along.scale(0.5)).subtract(cameraPos));
        if (side.lengthSqr() < 1e-8) {
            side = axis.cross(cameraUp);
        }
        side = side.normalize();
        uniforms(style, progress, (float) length, (float) width);
        quad(from, axis, side, -margin, length + margin, -halfBreadth, halfBreadth);
    }

    private void uniforms(int style, float progress, float extentX, float extentY) {
        shader.safeGetUniform("Style").set(style);
        shader.safeGetUniform("Progress").set(progress);
        shader.safeGetUniform("Extent").set(extentX, extentY);
        shader.safeGetUniform("Segment").set(segmentX, segmentY, segmentZ, segmentW);
        segmentX = 0.0f;
        segmentY = 1.0f;
        segmentZ = 0.0f;
        segmentW = 1.0f;
    }

    private void quad(Vec3 origin, Vec3 u, Vec3 w, double u0, double u1, double w0, double w1) {
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, origin, u, w, u0, w0);
        vertex(builder, origin, u, w, u1, w0);
        vertex(builder, origin, u, w, u1, w1);
        vertex(builder, origin, u, w, u0, w1);
        BufferUploader.drawWithShader(builder.end());
    }

    private void bandVertex(BufferBuilder builder, Vec3 rim, double height, double around) {
        Vec3 point = rim.add(0.0, height, 0.0).subtract(cameraPos);
        builder.vertex(pose, (float) point.x, (float) point.y, (float) point.z)
            .uv((float) around, (float) height)
            .endVertex();
    }

    private void vertex(BufferBuilder builder, Vec3 origin, Vec3 u, Vec3 w, double a, double b) {
        Vec3 point = origin.add(u.scale(a)).add(w.scale(b)).subtract(cameraPos);
        builder.vertex(pose, (float) point.x, (float) point.y, (float) point.z)
            .uv((float) a, (float) b)
            .endVertex();
    }
}
