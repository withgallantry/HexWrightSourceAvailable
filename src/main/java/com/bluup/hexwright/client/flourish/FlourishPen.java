package com.bluup.hexwright.client.flourish;

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
import org.joml.Matrix4f;
import org.joml.Vector3f;

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

    public static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private static final double DISC_MARGIN = 1.12;

    private static final int BAND_FACES = 32;

    private final ShaderInstance shader;
    private final Matrix4f pose;
    private final Vec3 cameraPos;
    private final Vec3 cameraUp;
    private final Vec3 cameraRight;
    private final float partialTick;

    private float segmentX;
    private float segmentY = 1.0f;
    private float segmentZ;
    private float segmentW = 1.0f;

    FlourishPen(ShaderInstance shader, Matrix4f pose, Camera camera, float partialTick) {
        this.shader = shader;
        this.pose = pose;
        this.cameraPos = camera.getPosition();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        this.cameraUp = new Vec3(up.x(), up.y(), up.z());
        this.cameraRight = new Vec3(-left.x(), -left.y(), -left.z());
        this.partialTick = partialTick;
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

    public FlourishPen segment(float x, float y, float z, float w) {
        segmentX = x;
        segmentY = y;
        segmentZ = z;
        segmentW = w;
        return this;
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
        Vec3 side = axis.cross(from.add(along.scale(0.5)).subtract(cameraPos));
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
