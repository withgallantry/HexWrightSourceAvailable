package com.bluup.hexwright.client.malediction;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.client.region.RegionShell;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.server.region.Region;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MaledictionWarnings {

    private static final long FADE_IN_MS = 150L;
    private static final long FADE_OUT_MS = 400L;
    private static final long FLASH_MS = 350L;
    private static final int MAX_WARNINGS = 16;
    private static final float BREATH_DEPTH = 0.25F;
    private static final float BREATH_MS = 1_600.0F;
    private static final float CORONA_WIDTH = 0.6F;
    private static final double CULL_MARGIN = 3.0;

    private static final int PART_SHELL = 0;
    private static final int PART_CORONA = 1;
    private static final int PART_TENDRIL = 2;

    private static final int VERTICES_PER_TENDRIL = 50;
    private static final int MIN_TENDRILS = 8;
    private static final int MAX_TENDRILS = 48;
    private static final int TENDRIL_SEGMENTS = 8;
    private static final float TENDRIL_LENGTH = 1.1F;
    private static final float TENDRIL_HALF_WIDTH = 0.13F;
    private static final float TENDRIL_SWAY = 0.6F;
    private static final long TENDRIL_LIFE_MS = 600L;
    private static final float TENDRIL_GROW = 0.25F;
    private static final float TENDRIL_LASH = 1.5F;
    private static final int SPROUT_TRIES = 4;

    private static final List<Warning> LIVE = new ArrayList<>();
    private static final BufferBuilder BUILDER = new BufferBuilder(4096);

    private MaledictionWarnings() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(MaledictionWarnings::render);
    }

    public static void warn(int id, @Nullable CompoundTag regionTag, int colour, int landTicks, int lifetimeTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || regionTag == null) {
            return;
        }
        Region region;
        try {
            region = Region.load(regionTag);
        } catch (IllegalArgumentException malformed) {
            Hexwright.LOGGER.warn("Ignoring a malformed Malediction warning", malformed);
            return;
        }
        if (region.isEmpty()) {
            return;
        }
        CompletableFuture<RegionShell> shell = CompletableFuture.supplyAsync(() -> trace(region), Util.backgroundExecutor())
            .exceptionally(failure -> {
                Hexwright.LOGGER.error("A Malediction warning could not trace its Region", failure);
                return null;
            });
        while (LIVE.size() >= MAX_WARNINGS) {
            LIVE.remove(0).close();
        }
        long now = Util.getMillis();
        LIVE.add(new Warning(id, level, colour, shell, now, now + landTicks * 50L, now + lifetimeTicks * 50L));
    }

    public static void landed(int id, boolean landed, int nextTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        long now = Util.getMillis();
        for (Warning warning : LIVE) {
            if (warning.id == id && warning.level == level) {
                warning.landed(now, landed, nextTicks);
            }
        }
    }

    private static RegionShell trace(Region region) {
        long started = System.nanoTime();
        RegionShell shell = RegionShell.trace(region);
        HexwrightDebug.log(HexwrightDebug.RENDER, "[malediction] warning traced {} vertices in {} ms",
            shell.vertexCount(), (System.nanoTime() - started) / 1_000_000L);
        return shell;
    }

    private static void render(WorldRenderContext context) {
        if (LIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        LIVE.removeIf(warning -> {
            boolean over = now >= warning.endsAt;
            if (over) {
                warning.close();
            }
            return over;
        });
        ShaderInstance shader = MaledictionWarningShaders.shader();
        if (mc.level == null || LIVE.isEmpty() || shader == null) {
            return;
        }

        Frustum frustum = context.frustum();
        List<Warning> visible = new ArrayList<>(LIVE.size());
        for (Warning warning : LIVE) {
            RegionShell shell = warning.shell.getNow(null);
            if (warning.level == mc.level && shell != null
                && (frustum == null || frustum.isVisible(shell.box().inflate(CULL_MARGIN)))) {
                visible.add(warning);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        Vec3 camera = context.camera().getPosition();
        Matrix4f view = context.matrixStack().last().pose();
        Matrix4f projection = context.projectionMatrix();
        float seconds = (now % 3_600_000L) / 1000.0F;

        BlendMode previousBlend = BlendModeAccessor.hexwright$getLastApplied();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            shader.safeGetUniform("Time").set(seconds);
            shader.safeGetUniform("CoronaWidth").set(CORONA_WIDTH);
            for (Warning warning : visible) {
                warning.draw(shader, view, projection, camera, now, seconds);
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            BlendModeAccessor.hexwright$setLastApplied(previousBlend);
        }
    }

    private static final class Warning {

        final int id;
        final ClientLevel level;
        final int colour;
        final CompletableFuture<RegionShell> shell;
        final long shownAt;
        final RandomSource random = RandomSource.create();
        long chargedFrom;
        long landsAt;
        long endsAt;
        boolean flashed;
        long flashedAt;
        private @Nullable VertexBuffer mesh;
        private @Nullable VertexBuffer tendrilMesh;
        private Tendril[] tendrils;
        private float[] reach;
        private float reachRadius;
        private float middleX;
        private float middleZ;

        Warning(int id, ClientLevel level, int colour, CompletableFuture<RegionShell> shell, long shownAt,
                long landsAt, long endsAt) {
            this.id = id;
            this.level = level;
            this.colour = colour;
            this.shell = shell;
            this.shownAt = shownAt;
            this.chargedFrom = shownAt;
            this.landsAt = landsAt;
            this.endsAt = endsAt;
        }

        void landed(long now, boolean landed, int nextTicks) {
            if (!landed) {
                endsAt = Math.min(endsAt, now + FADE_OUT_MS);
                return;
            }
            flashed = true;
            flashedAt = now;
            if (nextTicks > 0) {
                chargedFrom = now;
                landsAt = now + nextTicks * 50L;
            } else {
                landsAt = now;
                endsAt = Math.min(endsAt, now + FLASH_MS + FADE_OUT_MS);
            }
        }

        void close() {
            if (mesh != null) {
                mesh.close();
                mesh = null;
            }
            if (tendrilMesh != null) {
                tendrilMesh.close();
                tendrilMesh = null;
            }
        }

        void draw(ShaderInstance shader, Matrix4f view, Matrix4f projection, Vec3 camera, long now, float seconds) {
            RegionShell traced = shell.getNow(null);
            if (traced == null) {
                return;
            }
            if (mesh == null) {
                upload(traced);
            }

            float fade = Mth.clamp(Math.min((now - shownAt) / (float) FADE_IN_MS, (endsAt - now) / (float) FADE_OUT_MS),
                0.0F, 1.0F);
            boolean charging = now < landsAt;
            float charge = charging ? (now - chargedFrom) / (float) (landsAt - chargedFrom) : 1.0F;
            float flash = flashed ? Math.max(0.0F, 1.0F - (now - flashedAt) / (float) FLASH_MS) : 0.0F;
            flash *= flash;
            float breath = charging ? 1.0F
                : 1.0F - BREATH_DEPTH * 0.5F * (1.0F - Mth.cos((now - landsAt) / BREATH_MS * Mth.TWO_PI));

            float originX = (float) (traced.originX() - camera.x);
            float originY = (float) (traced.originY() - camera.y);
            float originZ = (float) (traced.originZ() - camera.z);
            Matrix4f modelView = new Matrix4f(view).translate(originX, originY, originZ);
            shader.safeGetUniform("Origin").set(originX, originY, originZ);
            shader.safeGetUniform("Seed").set((id * 0.618034F) % 1.0F * 100.0F);
            shader.safeGetUniform("Colour").set(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F);
            shader.safeGetUniform("Middle").set(middleX, middleZ);
            shader.safeGetUniform("Reach").set(reachRadius);
            shader.safeGetUniform("Charge").set(charge);
            shader.safeGetUniform("Charging").set(charging ? 1.0F : 0.0F);
            shader.safeGetUniform("Flash").set(flash);
            shader.safeGetUniform("Fade").set(fade * breath);

            mesh.bind();
            shader.safeGetUniform("Part").set(PART_CORONA);
            mesh.drawWithShader(modelView, projection, shader);
            shader.safeGetUniform("Part").set(PART_SHELL);
            shader.safeGetUniform("Side").set(-1.0F);
            mesh.drawWithShader(modelView, projection, shader);
            shader.safeGetUniform("Side").set(1.0F);
            mesh.drawWithShader(modelView, projection, shader);

            Vector3f eye = new Vector3f(-originX, -originY, -originZ);
            if (buildTendrils(traced, now, seconds, charge, charging, flash, eye)) {
                tendrilMesh.bind();
                tendrilMesh.upload(BUILDER.end());
                shader.safeGetUniform("Part").set(PART_TENDRIL);
                tendrilMesh.drawWithShader(modelView, projection, shader);
            }
        }

        private void upload(RegionShell traced) {
            float[] positions = traced.positions();
            float[] normals = traced.normals();
            BUILDER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            for (int vertex : traced.quads()) {
                BUILDER.vertex(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])
                    .uv(0.0F, 0.0F)
                    .color(255, 255, 255, 255)
                    .normal(normals[vertex * 3], normals[vertex * 3 + 1], normals[vertex * 3 + 2])
                    .endVertex();
            }
            mesh = new VertexBuffer(VertexBuffer.Usage.STATIC);
            mesh.bind();
            mesh.upload(BUILDER.end());
            tendrilMesh = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);

            int vertices = traced.vertexCount();
            middleX = (float) (traced.box().getCenter().x - traced.originX());
            middleZ = (float) (traced.box().getCenter().z - traced.originZ());
            reach = new float[vertices];
            float farthest = 0.5F;
            for (int v = 0; v < vertices; v++) {
                float dx = positions[v * 3] - middleX;
                float dz = positions[v * 3 + 2] - middleZ;
                reach[v] = Mth.sqrt(dx * dx + dz * dz);
                farthest = Math.max(farthest, reach[v]);
            }
            for (int v = 0; v < vertices; v++) {
                reach[v] /= farthest;
            }
            reachRadius = farthest;
            tendrils = new Tendril[Mth.clamp(vertices / VERTICES_PER_TENDRIL, MIN_TENDRILS, MAX_TENDRILS)];
            for (int i = 0; i < tendrils.length; i++) {
                tendrils[i] = new Tendril();
            }
        }

        private boolean buildTendrils(RegionShell traced, long now, float seconds, float charge, boolean charging,
                                      float flash, Vector3f eye) {
            float[] positions = traced.positions();
            float[] normals = traced.normals();
            boolean begun = false;
            Vector3f[] points = new Vector3f[TENDRIL_SEGMENTS + 1];
            Vector3f[] sides = new Vector3f[TENDRIL_SEGMENTS + 1];
            for (Tendril tendril : tendrils) {
                if (tendril.anchor < 0 || now - tendril.bornAt >= tendril.life) {
                    sprout(tendril, now, charge, charging);
                }
                if (tendril.anchor < 0) {
                    continue;
                }
                int anchor = tendril.anchor * 3;
                Vector3f normal = new Vector3f(normals[anchor], normals[anchor + 1], normals[anchor + 2]);
                if (normal.lengthSquared() < 1.0e-6F) {
                    continue;
                }
                float age = (now - tendril.bornAt) / (float) tendril.life;
                float length = tendril.length * Math.min(1.0F, age / TENDRIL_GROW)
                    * (charging ? 0.5F + 0.6F * charge : 0.8F) * (1.0F + TENDRIL_LASH * flash);
                Vector3f across = Math.abs(normal.y) < 0.9F ? normal.cross(0.0F, 1.0F, 0.0F, new Vector3f())
                    : normal.cross(1.0F, 0.0F, 0.0F, new Vector3f());
                across.normalize();
                Vector3f up = normal.cross(across, new Vector3f());
                Vector3f heading = new Vector3f(normal).fma(tendril.leanU, across).fma(tendril.leanV, up).normalize();

                for (int k = 0; k <= TENDRIL_SEGMENTS; k++) {
                    float s = k / (float) TENDRIL_SEGMENTS;
                    float sway = length * TENDRIL_SWAY * s * Mth.sqrt(s);
                    points[k] = new Vector3f(positions[anchor], positions[anchor + 1], positions[anchor + 2])
                        .fma(s * length, heading)
                        .fma(Mth.sin(seconds * 2.3F + s * 4.0F + tendril.phase) * sway, across)
                        .fma(Mth.sin(seconds * 1.7F + s * 3.3F + tendril.phase * 1.9F) * sway, up);
                }
                for (int k = 0; k <= TENDRIL_SEGMENTS; k++) {
                    Vector3f tangent = new Vector3f(points[Math.min(k + 1, TENDRIL_SEGMENTS)]).sub(points[Math.max(k - 1, 0)]);
                    Vector3f side = tangent.cross(new Vector3f(eye).sub(points[k]));
                    if (side.lengthSquared() < 1.0e-8F) {
                        side.set(across);
                    }
                    float halfWidth = TENDRIL_HALF_WIDTH * (1.0F - 0.85F * k / TENDRIL_SEGMENTS);
                    sides[k] = side.normalize(halfWidth);
                }

                if (!begun) {
                    BUILDER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
                    begun = true;
                }
                int seed = (int) (tendril.seed * 255.0F);
                int life = (int) (Mth.sin(age * Mth.PI) * 255.0F);
                for (int k = 0; k < TENDRIL_SEGMENTS; k++) {
                    float from = k / (float) TENDRIL_SEGMENTS;
                    float to = (k + 1) / (float) TENDRIL_SEGMENTS;
                    ribbonVertex(points[k], sides[k], -1.0F, from, seed, life);
                    ribbonVertex(points[k], sides[k], 1.0F, from, seed, life);
                    ribbonVertex(points[k + 1], sides[k + 1], 1.0F, to, seed, life);
                    ribbonVertex(points[k + 1], sides[k + 1], -1.0F, to, seed, life);
                }
            }
            return begun;
        }

        private static void ribbonVertex(Vector3f point, Vector3f side, float edge, float along, int seed, int life) {
            BUILDER.vertex(point.x + side.x * edge, point.y + side.y * edge, point.z + side.z * edge)
                .uv(along, edge)
                .color(seed, 0, 0, life)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
        }

        private void sprout(Tendril tendril, long now, float charge, boolean charging) {
            tendril.anchor = -1;
            for (int attempt = 0; attempt < SPROUT_TRIES; attempt++) {
                int vertex = random.nextInt(reach.length);
                if (!charging || reach[vertex] <= charge) {
                    tendril.anchor = vertex;
                    tendril.bornAt = now;
                    tendril.life = TENDRIL_LIFE_MS + random.nextInt((int) TENDRIL_LIFE_MS);
                    tendril.length = TENDRIL_LENGTH * (0.6F + 0.8F * random.nextFloat());
                    tendril.phase = random.nextFloat() * Mth.TWO_PI;
                    tendril.seed = random.nextFloat();
                    tendril.leanU = random.nextFloat() - 0.5F;
                    tendril.leanV = random.nextFloat() - 0.5F;
                    return;
                }
            }
        }
    }

    private static final class Tendril {
        int anchor = -1;
        long bornAt;
        long life;
        float length;
        float phase;
        float seed;
        float leanU;
        float leanV;
    }
}
