package com.bluup.hexwright.client.hexahedron;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class WardCrystal {

    private static final float FAR_SHARE = 0.9f;

    public record Shape(float halfLength, float radius, float body, float texel) {

        public Shape(float halfLength, float radius, float body) {
            this(halfLength, radius, body, 0.5F);
        }
    }

    public record Look(float fill, float glow, float flare, float dissolve, float fade, float seed) {
    }

    private WardCrystal() {
    }

    public static @Nullable ShaderInstance shader(float time, float daylight) {
        ShaderInstance shader = HexahedronShaders.crystal();
        if (shader != null) {
            shader.safeGetUniform("Time").set(time);
            shader.safeGetUniform("Daylight").set(daylight);
        }
        return shader;
    }

    public static void draw(ShaderInstance shader, Vec3 centre, Matrix3f basis, Shape shape, Look look, Vec3 eye) {
        Vector3f middle = new Vector3f((float) (centre.x - eye.x), (float) (centre.y - eye.y), (float) (centre.z - eye.z));
        float scale = shrink(middle.length() + shape.halfLength(), reach());
        shader.safeGetUniform("Fill").set(look.fill());
        shader.safeGetUniform("Glow").set(look.glow());
        shader.safeGetUniform("Flare").set(look.flare());
        shader.safeGetUniform("Dissolve").set(look.dissolve());
        shader.safeGetUniform("Fade").set(look.fade());
        shader.safeGetUniform("Seed").set(look.seed());
        shader.safeGetUniform("Scale").set(scale);
        shader.safeGetUniform("CameraOffset").set(-middle.x, -middle.y, -middle.z);
        shader.safeGetUniform("CrystalBasis").set(basis);
        shader.safeGetUniform("HalfLength").set(shape.halfLength());
        shader.safeGetUniform("Radius").set(shape.radius());
        shader.safeGetUniform("Body").set(shape.body());
        shader.safeGetUniform("Texel").set(shape.texel());
        Vector3f[] upper = new Vector3f[6];
        Vector3f[] lower = new Vector3f[6];
        for (int k = 0; k < 6; k++) {
            float angle = k * Mth.PI / 3.0f;
            float x = shape.radius() * Mth.cos(angle);
            float z = shape.radius() * Mth.sin(angle);
            upper[k] = onCrystal(basis, middle, x, shape.body(), z);
            lower[k] = onCrystal(basis, middle, x, -shape.body(), z);
        }
        Vector3f top = onCrystal(basis, middle, 0.0f, shape.halfLength(), 0.0f);
        Vector3f bottom = onCrystal(basis, middle, 0.0f, -shape.halfLength(), 0.0f);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        for (int k = 0; k < 6; k++) {
            int next = (k + 1) % 6;
            triangle(builder, scale, lower[k], lower[next], upper[next]);
            triangle(builder, scale, lower[k], upper[next], upper[k]);
            triangle(builder, scale, upper[k], upper[next], top);
            triangle(builder, scale, lower[next], lower[k], bottom);
        }
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    static float reach() {
        return FAR_SHARE * Minecraft.getInstance().gameRenderer.getDepthFar();
    }

    static float shrink(double distance, float reach) {
        return (float) Math.max(1.0, distance / reach);
    }

    private static Vector3f onCrystal(Matrix3f basis, Vector3f middle, float x, float y, float z) {
        return basis.transform(new Vector3f(x, y, z)).add(middle);
    }

    private static void triangle(BufferBuilder builder, float scale, Vector3f a, Vector3f b, Vector3f c) {
        for (Vector3f point : new Vector3f[]{a, b, c}) {
            builder.vertex(point.x / scale, point.y / scale, point.z / scale).endVertex();
        }
    }
}
