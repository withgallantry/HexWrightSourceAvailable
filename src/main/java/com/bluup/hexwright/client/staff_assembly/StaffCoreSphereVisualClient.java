package com.bluup.hexwright.client.staff_assembly;

import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.client.render.SceneSnapshot;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class StaffCoreSphereVisualClient {
    private static final ResourceLocation SHIELD_WHITE = new ResourceLocation("hexwright", "textures/effects/shield_white.png");
    private static final RenderType SHIELD_TYPE = RenderType.entityTranslucent(SHIELD_WHITE);
    private static final RenderType BAND_TYPE = RenderType.entityTranslucentCull(SHIELD_WHITE);
    private static final int GRID_X = 28;
    private static final int GRID_Y = 28;
    private static final float OPEN_CLOSE_TICKS = 18.0f;
    private static final float REVEAL_SOFTNESS = 0.14f;
    private static final float NOISE_SCALE = 0.20f;
    private static final float NOISE_TIME_SCALE = 0.18f;
    private static final float FACE_ALPHA = 0.24f;
    private static final float SOFT_PULSE_TIME_SCALE = 0.45f;
    private static final float SOFT_PULSE_ALPHA_AMPLITUDE = 0.32f;
    private static final float SOFT_PULSE_BRIGHTNESS_AMPLITUDE = 0.60f;
    private static final float PERIMETER_EDGE_ALPHA = 0.86f;
    private static final float OUTLINE_THICKNESS = 0.060f;
    private static final float EDGE_OFFSET = 0.015f;

    private static final float CONTACT_GLOW_WIDTH = 0.085f;
    private static final float NEAR_PLANE = 0.05f;
    private static final double INK_WRAP = 4096.0;

    private static final float DEBUG_MODE = intProperty("hexwright.shield.debug", 0);

    private static final float DEBUG_FACE = intProperty("hexwright.shield.face", -1);

    private static final boolean DRAW_FACES = intProperty("hexwright.shield.faces", 1) != 0;

    private static final boolean DRAW_BANDS = intProperty("hexwright.shield.bands", 1) != 0;

    private static final boolean DRAW_GLOW = intProperty("hexwright.shield.glow", 1) != 0;

    private static final int FALLBACK_FACE_COLOR = 0x8FA6FF;
    private static final float EDGE_LIGHTEN = 0.35f;
    private static final float CONTACT_GLOW_LIGHTEN = 0.0f;

    private static final Map<Integer, CubeState> ACTIVE_CUBES = new HashMap<>();

    private static boolean announcedSwitches;

    private static int intProperty(String key, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            Hexwright.LOGGER.warn("Ignoring -D{}=\"{}\" - not a number, so this switch did nothing."
                + " Gradle takes one -D per argument; quoting several together makes the first"
                + " property swallow the rest of the line.", key, raw);
            return fallback;
        }
    }

    private StaffCoreSphereVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StaffCoreSphereVisualClient::onClientTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> render(Minecraft.getInstance(), context));
    }

    public static void handleSphereVisual(int entityId, boolean active, @Nullable CompoundTag pigmentTag, double halfExtent) {
        CubeState state = ACTIVE_CUBES.computeIfAbsent(entityId, ignored -> new CubeState());
        state.targetActive = active;
        if (active) {
            state.progress = Math.max(state.progress, 0.04f);
            state.halfExtent = (float) halfExtent;
            if (pigmentTag != null) {
                state.pigment = FrozenPigment.fromNBT(pigmentTag);
            }
        }
    }

    private static void onClientTick(Minecraft mc) {
        if (ACTIVE_CUBES.isEmpty()) {
            return;
        }

        Level level = mc.level;
        float step = 1.0f / OPEN_CLOSE_TICKS;
        Iterator<Map.Entry<Integer, CubeState>> it = ACTIVE_CUBES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, CubeState> entry = it.next();
            CubeState state = entry.getValue();
            state.progress = Mth.clamp(state.progress + (state.targetActive ? step : -step), 0.0f, 1.0f);

            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity == null) {
                if (!state.targetActive || state.progress <= 0.0f) {
                    state.dispose();
                    it.remove();
                    continue;
                }
            }

            if (!state.targetActive && state.progress <= 0.0f) {
                state.dispose();
                it.remove();
                continue;
            }

            if (entity != null && ShieldContactShaders.shader() != null && !IrisCompat.isShaderPackActive()) {
                Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
                state.field().rebuild(level, center.x, center.y, center.z, state.halfExtent);
            }
        }
    }

    private static void render(Minecraft mc, WorldRenderContext context) {
        if (mc.level == null || ACTIVE_CUBES.isEmpty()) {
            return;
        }

        MultiBufferSource consumers = context.consumers();
        if (consumers == null) {
            return;
        }

        float partialTick = mc.getFrameTime();
        long gameTime = mc.level.getGameTime();
        float time = (gameTime + partialTick) * 0.06f;

        List<LiveCube> live = new ArrayList<>();
        for (Map.Entry<Integer, CubeState> entry : ACTIVE_CUBES.entrySet()) {
            CubeState state = entry.getValue();
            if (state.progress <= 0.001f) {
                continue;
            }

            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                continue;
            }

            Vec3 center = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
            live.add(new LiveCube(
                center,
                state.halfExtent,
                easeOutCubic(state.progress),
                resolveBaseColor(state.pigment, gameTime + partialTick, center),
                softRandomPulse(time + state.pulseSeed),
                state.contactField
            ));
        }
        if (live.isEmpty()) {
            return;
        }

        if (!announcedSwitches) {
            announcedSwitches = true;
            Hexwright.LOGGER.info("Shield visual: faces={} glow={} debug={} face={}"
                    + " (a switch that never arrives looks exactly like a switch that did nothing)",
                DRAW_FACES, DRAW_GLOW, DEBUG_MODE, DEBUG_FACE);
        }

        int contactDepth = 0;
        if (ShieldContactShaders.shader() != null && !IrisCompat.isShaderPackActive()) {
            contactDepth = contactDepthTexture(mc);
        }

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = context.matrixStack();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        try {
            if (DRAW_BANDS) {
                VertexConsumer bandVc = consumers.getBuffer(BAND_TYPE);
                for (LiveCube cube : live) {
                    renderCubeBands(bandVc, poseStack, cube);
                }
                if (consumers instanceof MultiBufferSource.BufferSource bufferSource) {
                    bufferSource.endBatch(BAND_TYPE);
                }
            }

            if (DRAW_FACES) {
                VertexConsumer vc = consumers.getBuffer(SHIELD_TYPE);
                for (LiveCube cube : live) {
                    renderCube(vc, poseStack, cube, time);
                }
            }
        } finally {
            poseStack.popPose();
        }

        if (consumers instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(SHIELD_TYPE);
        }
        drawContactGlow(mc, context, camera, live, contactDepth);
    }

    public static void captureSolidDepth() {
    }

    private static void renderCubeBands(VertexConsumer vc, PoseStack poseStack, LiveCube cube) {
        Vec3 center = cube.center();
        float halfExtent = cube.halfExtent();
        PoseStack.Pose pose = poseStack.last();
        drawPerimeterEdgeBands(
            vc,
            pose.pose(),
            pose.normal(),
            (float) center.x - halfExtent,
            (float) center.x + halfExtent,
            (float) center.y - halfExtent,
            (float) center.y + halfExtent,
            (float) center.z - halfExtent,
            (float) center.z + halfExtent,
            cube.easedProgress(),
            lighten(cube.baseColor(), EDGE_LIGHTEN)
        );
    }

    private static void renderCube(VertexConsumer vc, PoseStack poseStack, LiveCube cube, float time) {
        Vec3 center = cube.center();
        float halfExtent = cube.halfExtent();
        float minX = (float) center.x - halfExtent;
        float maxX = (float) center.x + halfExtent;
        float minY = (float) center.y - halfExtent;
        float maxY = (float) center.y + halfExtent;
        float minZ = (float) center.z - halfExtent;
        float maxZ = (float) center.z + halfExtent;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normal = pose.normal();
        float easedProgress = cube.easedProgress();
        float pulse = cube.pulse();
        int baseColor = cube.baseColor();

        drawProceduralFace(vc, mat, normal, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, easedProgress, time, baseColor, FACE_ALPHA, pulse, 1.0f, 0.0f, 0.0f, 0.0f);
        drawProceduralFace(vc, mat, normal, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, easedProgress, time, baseColor, FACE_ALPHA, pulse, -1.0f, 0.0f, 0.0f, 0.0f);
        drawProceduralFace(vc, mat, normal, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, easedProgress, time, baseColor, FACE_ALPHA, pulse, 0.0f, 1.0f, 0.0f, 0.0f);
        drawProceduralFace(vc, mat, normal, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, easedProgress, time, baseColor, FACE_ALPHA, pulse, 0.0f, -1.0f, 0.0f, 0.0f);
        drawProceduralFace(vc, mat, normal, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, easedProgress, time, baseColor, FACE_ALPHA, pulse, 0.0f, 0.0f, 1.0f, 0.0f);
        drawProceduralFace(vc, mat, normal, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, minX, minY, minZ, easedProgress, time, baseColor, FACE_ALPHA, pulse, 0.0f, 0.0f, -1.0f, 0.0f);
    }

    private static void drawContactGlow(
        Minecraft mc,
        WorldRenderContext context,
        Vec3 camera,
        List<LiveCube> live,
        int depthTexture
    ) {
        ShaderInstance shader = ShieldContactShaders.shader();
        if (!DRAW_GLOW || shader == null || depthTexture <= 0) {
            return;
        }

        Matrix4f cameraRotation = context.matrixStack().last().pose();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        modelViewStack.mulPoseMatrix(cameraRotation);
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        shader.safeGetUniform("NearFar").set(NEAR_PLANE, mc.gameRenderer.getDepthFar());
        shader.safeGetUniform("GlowWidth").set(CONTACT_GLOW_WIDTH);
        shader.safeGetUniform("FieldRange").set(ShieldContactField.RANGE);
        shader.safeGetUniform("DebugMode").set(DEBUG_MODE);
        shader.safeGetUniform("DebugFace").set(DEBUG_FACE);
        shader.safeGetUniform("InkOrigin").set(
            (float) wrapInkOrigin(camera.x),
            (float) wrapInkOrigin(camera.y),
            (float) wrapInkOrigin(camera.z)
        );
        shader.setSampler("SamplerSceneDepth", depthTexture);
        RenderSystem.setShader(() -> shader);

        for (LiveCube cube : live) {
            ShieldContactField field = cube.field();
            if (field == null || !field.ready()) {
                continue;
            }
            int alpha = Mth.clamp((int) (255.0f * cube.easedProgress()), 0, 255);
            if (alpha <= 0) {
                continue;
            }
            int rgb = lighten(cube.baseColor(), CONTACT_GLOW_LIGHTEN);
            shader.safeGetUniform("FieldCell").set(field.cellSize());
            shader.setSampler("SamplerContactField", field.textureId());

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            glowBox(builder, cube, camera, field,
                (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
            BufferUploader.drawWithShader(builder.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static int contactDepthTexture(Minecraft mc) {
        if (Minecraft.useShaderTransparency()) {
            RenderTarget translucent = mc.levelRenderer.getTranslucentTarget();
            if (translucent != null && translucent.useDepth) {
                return translucent.getDepthTextureId();
            }
        }
        return SceneSnapshot.captureDepthOnly(SceneSnapshot.SLOT_SHIELD_CONTACT)
            ? SceneSnapshot.depthTextureId()
            : 0;
    }

    private static double wrapInkOrigin(double cameraComponent) {
        return cameraComponent - (Math.floor(cameraComponent / INK_WRAP) * INK_WRAP);
    }

    private static final float[] CORNER_U = {0.0f, 0.0f, 1.0f, 1.0f};
    private static final float[] CORNER_V = {0.0f, 1.0f, 1.0f, 0.0f};

    private static void glowBox(
        BufferBuilder builder,
        LiveCube cube,
        Vec3 camera,
        ShieldContactField field,
        int r,
        int g,
        int b,
        int a
    ) {
        Vec3 center = cube.center();
        float half = cube.halfExtent();
        double span = 2.0 * half;
        double[] centerWorld = {center.x, center.y, center.z};
        double[] cameraWorld = {camera.x, camera.y, camera.z};
        double[] corner = new double[3];

        for (int face = 0; face < ShieldContactField.FACE_COUNT; face++) {
            int planeAxis = ShieldContactField.faceAxis(face);
            int uAxis = ShieldContactField.faceU(face);
            int vAxis = ShieldContactField.faceV(face);
            double plane = centerWorld[planeAxis] + ShieldContactField.faceSide(face) * half;
            double uMin = centerWorld[uAxis] - half;
            double vMin = centerWorld[vAxis] - half;

            for (int i = 0; i < 4; i++) {
                double u = uMin + CORNER_U[i] * span;
                double v = vMin + CORNER_V[i] * span;
                corner[planeAxis] = plane;
                corner[uAxis] = u;
                corner[vAxis] = v;
                builder.vertex(
                        (float) (corner[0] - cameraWorld[0]),
                        (float) (corner[1] - cameraWorld[1]),
                        (float) (corner[2] - cameraWorld[2]))
                    .color(r, g, b, a)
                    .uv(field.atlasU(face, field.fraction(uAxis, u)),
                        field.atlasV(face, field.fraction(vAxis, v)))
                    .endVertex();
            }
        }
    }

    private static void drawProceduralFace(
        VertexConsumer vc,
        Matrix4f mat,
        Matrix3f normal,
        float x00,
        float y00,
        float z00,
        float x01,
        float y01,
        float z01,
        float x11,
        float y11,
        float z11,
        float x10,
        float y10,
        float z10,
        float revealProgress,
        float time,
        int rgb,
        float alpha,
        float pulse,
        float normalX,
        float normalY,
        float normalZ,
        float normalOffset
    ) {
        float baseR = ((rgb >> 16) & 0xFF) / 255.0f;
        float baseG = ((rgb >> 8) & 0xFF) / 255.0f;
        float baseB = (rgb & 0xFF) / 255.0f;

        for (int xi = 0; xi < GRID_X; xi++) {
            float u0 = xi / (float) GRID_X;
            float u1 = (xi + 1) / (float) GRID_X;

            for (int yi = 0; yi < GRID_Y; yi++) {
                float v0 = yi / (float) GRID_Y;
                float v1 = (yi + 1) / (float) GRID_Y;
                float p0x = lerpFaceX(x00, x01, x11, x10, u0, v0);
                float p0y = lerpFaceY(y00, y01, y11, y10, u0, v0);
                float p0z = lerpFaceZ(z00, z01, z11, z10, u0, v0);
                float p1x = lerpFaceX(x00, x01, x11, x10, u0, v1);
                float p1y = lerpFaceY(y00, y01, y11, y10, u0, v1);
                float p1z = lerpFaceZ(z00, z01, z11, z10, u0, v1);
                float p2x = lerpFaceX(x00, x01, x11, x10, u1, v1);
                float p2y = lerpFaceY(y00, y01, y11, y10, u1, v1);
                float p2z = lerpFaceZ(z00, z01, z11, z10, u1, v1);
                float p3x = lerpFaceX(x00, x01, x11, x10, u1, v0);
                float p3y = lerpFaceY(y00, y01, y11, y10, u1, v0);
                float p3z = lerpFaceZ(z00, z01, z11, z10, u1, v0);

                float a0 = sampleFaceAlpha(p0x, p0y, p0z, u0, v0, revealProgress, time, alpha, pulse);
                float a1 = sampleFaceAlpha(p1x, p1y, p1z, u0, v1, revealProgress, time, alpha, pulse);
                float a2 = sampleFaceAlpha(p2x, p2y, p2z, u1, v1, revealProgress, time, alpha, pulse);
                float a3 = sampleFaceAlpha(p3x, p3y, p3z, u1, v0, revealProgress, time, alpha, pulse);
                float maxA = Math.max(Math.max(a0, a1), Math.max(a2, a3));
                if (maxA <= 1.0f / 255.0f) {
                    continue;
                }

                float dark0 = sampleFaceBrightness(u0, v0, pulse);
                float dark1 = sampleFaceBrightness(u0, v1, pulse);
                float dark2 = sampleFaceBrightness(u1, v1, pulse);
                float dark3 = sampleFaceBrightness(u1, v0, pulse);

                float ox = normalX * normalOffset;
                float oy = normalY * normalOffset;
                float oz = normalZ * normalOffset;
                quadGradient(
                    vc,
                    mat,
                    normal,
                    p0x + ox,
                    p0y + oy,
                    p0z + oz,
                    p1x + ox,
                    p1y + oy,
                    p1z + oz,
                    p2x + ox,
                    p2y + oy,
                    p2z + oz,
                    p3x + ox,
                    p3y + oy,
                    p3z + oz,
                    u0,
                    v0,
                    u1,
                    v1,
                    Mth.clamp((int) (255.0f * baseR * dark0), 0, 255),
                    Mth.clamp((int) (255.0f * baseG * dark0), 0, 255),
                    Mth.clamp((int) (255.0f * baseB * dark0), 0, 255),
                    Mth.clamp((int) (255.0f * a0), 0, 255),
                    Mth.clamp((int) (255.0f * baseR * dark1), 0, 255),
                    Mth.clamp((int) (255.0f * baseG * dark1), 0, 255),
                    Mth.clamp((int) (255.0f * baseB * dark1), 0, 255),
                    Mth.clamp((int) (255.0f * a1), 0, 255),
                    Mth.clamp((int) (255.0f * baseR * dark2), 0, 255),
                    Mth.clamp((int) (255.0f * baseG * dark2), 0, 255),
                    Mth.clamp((int) (255.0f * baseB * dark2), 0, 255),
                    Mth.clamp((int) (255.0f * a2), 0, 255),
                    Mth.clamp((int) (255.0f * baseR * dark3), 0, 255),
                    Mth.clamp((int) (255.0f * baseG * dark3), 0, 255),
                    Mth.clamp((int) (255.0f * baseB * dark3), 0, 255),
                    Mth.clamp((int) (255.0f * a3), 0, 255),
                    normalX,
                    normalY,
                    normalZ
                );
            }
        }
    }

    private static float sampleFaceAlpha(float x, float y, float z, float u, float v, float revealProgress, float time, float alpha, float pulse) {
        float noise = inkNoise(x, y, z, time);
        float reveal = smoothstep(noise - REVEAL_SOFTNESS, noise + REVEAL_SOFTNESS, revealProgress);
        float edgeOpacity = radialEdgeOpacity(u, v);
        float alphaScale = 0.26f + (0.54f * edgeOpacity) + (SOFT_PULSE_ALPHA_AMPLITUDE * pulse);
        return alpha * reveal * Mth.clamp(alphaScale, 0.0f, 1.0f);
    }

    private static float sampleFaceBrightness(float u, float v, float pulse) {
        float edgeOpacity = radialEdgeOpacity(u, v);
        return Mth.clamp(0.92f - (0.10f * edgeOpacity) + (SOFT_PULSE_BRIGHTNESS_AMPLITUDE * pulse), 0.72f, 1.58f);
    }

    private static float softRandomPulse(float time) {
        float scaledTime = time * SOFT_PULSE_TIME_SCALE;
        int t0 = Mth.floor(scaledTime);
        int t1 = t0 + 1;
        float frac = scaledTime - t0;
        float smooth = frac * frac * (3.0f - (2.0f * frac));
        float v0 = hash3ToUnit(t0, 0, 0);
        float v1 = hash3ToUnit(t1, 0, 0);
        return Mth.lerp(smooth, v0, v1);
    }

    private static int resolveBaseColor(@Nullable FrozenPigment pigment, float time, Vec3 pos) {
        if (pigment == null) {
            return FALLBACK_FACE_COLOR;
        }

        try {
            ColorProvider provider = IXplatAbstractions.INSTANCE.getColorProvider(pigment);
            return provider.getColor(time, pos) & 0xFFFFFF;
        } catch (RuntimeException ignored) {
            return FALLBACK_FACE_COLOR;
        }
    }

    private static int lighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int lr = Mth.clamp((int) Mth.lerp(amount, r, 255.0f), 0, 255);
        int lg = Mth.clamp((int) Mth.lerp(amount, g, 255.0f), 0, 255);
        int lb = Mth.clamp((int) Mth.lerp(amount, b, 255.0f), 0, 255);
        return (lr << 16) | (lg << 8) | lb;
    }

    private static float radialEdgeOpacity(float u, float v) {
        float du = (u - 0.5f) * 2.0f;
        float dv = (v - 0.5f) * 2.0f;
        float radius = Mth.clamp((float) Math.sqrt((du * du) + (dv * dv)), 0.0f, 1.0f);
        return smoothstep(0.48f, 0.98f, radius);
    }

    private static void drawPerimeterEdgeBands(
        VertexConsumer vc,
        Matrix4f mat,
        Matrix3f normal,
        float minX,
        float maxX,
        float minY,
        float maxY,
        float minZ,
        float maxZ,
        float openProgress,
        int rgb
    ) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = Mth.clamp((int) (255.0f * PERIMETER_EDGE_ALPHA * openProgress), 0, 255);
        if (a <= 0) {
            return;
        }

        float minXO = minX - EDGE_OFFSET;
        float maxXO = maxX + EDGE_OFFSET;
        float minYO = minY - EDGE_OFFSET;
        float maxYO = maxY + EDGE_OFFSET;
        float minZO = minZ - EDGE_OFFSET;
        float maxZO = maxZ + EDGE_OFFSET;

        drawFaceOutlineRect(vc, mat, normal, maxXO, true, minZ, maxZ, minY, maxY, r, g, b, a, 1.0f, 0.0f, 0.0f);
        drawFaceOutlineRect(vc, mat, normal, minXO, true, minZ, maxZ, minY, maxY, r, g, b, a, -1.0f, 0.0f, 0.0f);
        drawFaceOutlineRect(vc, mat, normal, maxZO, false, minX, maxX, minY, maxY, r, g, b, a, 0.0f, 0.0f, 1.0f);
        drawFaceOutlineRect(vc, mat, normal, minZO, false, minX, maxX, minY, maxY, r, g, b, a, 0.0f, 0.0f, -1.0f);
        drawHorizontalFaceOutlineRect(vc, mat, normal, maxYO, minX, maxX, minZ, maxZ, r, g, b, a, 0.0f, 1.0f, 0.0f);
        drawHorizontalFaceOutlineRect(vc, mat, normal, minYO, minX, maxX, minZ, maxZ, r, g, b, a, 0.0f, -1.0f, 0.0f);
    }

    private static void drawHorizontalFaceOutlineRect(
        VertexConsumer vc,
        Matrix4f mat,
        Matrix3f normal,
        float y,
        float xMin,
        float xMax,
        float zMin,
        float zMax,
        int r,
        int g,
        int b,
        int a,
        float nx,
        float ny,
        float nz
    ) {
        if (xMax - xMin <= 0.02f || zMax - zMin <= 0.02f) {
            return;
        }

        float h = zMax - zMin;
        float w = xMax - xMin;
        float t = Math.min(OUTLINE_THICKNESS, Math.min(h, w) * 0.45f);
        if (t <= 0.001f) {
            return;
        }

        quadTwoSided(vc, mat, normal, xMin, y, zMin, xMin, y, zMin + t, xMax, y, zMin + t, xMax, y, zMin, r, g, b, a, nx, ny, nz);
        quadTwoSided(vc, mat, normal, xMin, y, zMax - t, xMin, y, zMax, xMax, y, zMax, xMax, y, zMax - t, r, g, b, a, nx, ny, nz);
        quadTwoSided(vc, mat, normal, xMin, y, zMin + t, xMin, y, zMax - t, xMin + t, y, zMax - t, xMin + t, y, zMin + t, r, g, b, a, nx, ny, nz);
        quadTwoSided(vc, mat, normal, xMax - t, y, zMin + t, xMax - t, y, zMax - t, xMax, y, zMax - t, xMax, y, zMin + t, r, g, b, a, nx, ny, nz);
    }

    private static void drawFaceOutlineRect(
        VertexConsumer vc,
        Matrix4f mat,
        Matrix3f normal,
        float fixed,
        boolean fixedX,
        float spanMin,
        float spanMax,
        float yMin,
        float yMax,
        int r,
        int g,
        int b,
        int a,
        float nx,
        float ny,
        float nz
    ) {
        if (spanMax - spanMin <= 0.02f || yMax - yMin <= 0.02f) {
            return;
        }

        float h = yMax - yMin;
        float w = spanMax - spanMin;
        float t = Math.min(OUTLINE_THICKNESS, Math.min(h, w) * 0.45f);
        if (t <= 0.001f) {
            return;
        }

        if (fixedX) {
            quadTwoSided(vc, mat, normal, fixed, yMin, spanMin, fixed, yMin + t, spanMin, fixed, yMin + t, spanMax, fixed, yMin, spanMax, r, g, b, a, nx, ny, nz);
            quadTwoSided(vc, mat, normal, fixed, yMax - t, spanMin, fixed, yMax, spanMin, fixed, yMax, spanMax, fixed, yMax - t, spanMax, r, g, b, a, nx, ny, nz);
            quadTwoSided(vc, mat, normal, fixed, yMin + t, spanMin, fixed, yMax - t, spanMin, fixed, yMax - t, spanMin + t, fixed, yMin + t, spanMin + t, r, g, b, a, nx, ny, nz);
            quadTwoSided(vc, mat, normal, fixed, yMin + t, spanMax - t, fixed, yMax - t, spanMax - t, fixed, yMax - t, spanMax, fixed, yMin + t, spanMax, r, g, b, a, nx, ny, nz);
            return;
        }

        quadTwoSided(vc, mat, normal, spanMin, yMin, fixed, spanMin, yMin + t, fixed, spanMax, yMin + t, fixed, spanMax, yMin, fixed, r, g, b, a, nx, ny, nz);
        quadTwoSided(vc, mat, normal, spanMin, yMax - t, fixed, spanMin, yMax, fixed, spanMax, yMax, fixed, spanMax, yMax - t, fixed, r, g, b, a, nx, ny, nz);
        quadTwoSided(vc, mat, normal, spanMin, yMin + t, fixed, spanMin, yMax - t, fixed, spanMin + t, yMax - t, fixed, spanMin + t, yMin + t, fixed, r, g, b, a, nx, ny, nz);
        quadTwoSided(vc, mat, normal, spanMax - t, yMin + t, fixed, spanMax - t, yMax - t, fixed, spanMax, yMax - t, fixed, spanMax, yMin + t, fixed, r, g, b, a, nx, ny, nz);
    }

    private static void quadTwoSided(
        VertexConsumer vc,
        Matrix4f mat,
        Matrix3f normal,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3,
        int r,
        int g,
        int b,
        int a,
        float nx,
        float ny,
        float nz
    ) {
        quad(vc, mat, normal, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, 0.0f, 0.0f, 1.0f, 1.0f, r, g, b, a, nx, ny, nz);
        quad(vc, mat, normal, x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0, 0.0f, 0.0f, 1.0f, 1.0f, r, g, b, a, -nx, -ny, -nz);
    }

    private static float lerpFaceX(float x00, float x01, float x11, float x10, float u, float v) {
        return Mth.lerp(v, Mth.lerp(u, x00, x10), Mth.lerp(u, x01, x11));
    }

    private static float lerpFaceY(float y00, float y01, float y11, float y10, float u, float v) {
        return Mth.lerp(v, Mth.lerp(u, y00, y10), Mth.lerp(u, y01, y11));
    }

    private static float lerpFaceZ(float z00, float z01, float z11, float z10, float u, float v) {
        return Mth.lerp(v, Mth.lerp(u, z00, z10), Mth.lerp(u, z01, z11));
    }

    private static float easeOutCubic(float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        float inverse = 1.0f - clamped;
        return 1.0f - (inverse * inverse * inverse);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / Math.max(1.0E-5f, edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - (2.0f * t));
    }

    private static float inkNoise(float x, float y, float z, float time) {
        float nx = (x * NOISE_SCALE) + (time * NOISE_TIME_SCALE);
        float ny = (y * NOISE_SCALE * 0.85f) - (time * NOISE_TIME_SCALE * 0.62f);
        float nz = (z * NOISE_SCALE * 1.15f) + (time * NOISE_TIME_SCALE * 0.48f);

        float n1 = valueNoise3(nx, ny, nz);
        float n2 = valueNoise3((nx * 1.9f) - 31.7f, (ny * 1.9f) + 11.3f, (nz * 1.9f) - 7.1f);

        float base = (n1 * 0.66f) + (n2 * 0.34f);
        float swirl = 0.5f + (0.5f * Mth.sin((x * 0.11f) + (z * 0.09f) + (time * 0.7f)));
        return Mth.clamp((base * 0.82f) + (swirl * 0.18f), 0.0f, 1.0f);
    }

    private static float valueNoise3(float x, float y, float z) {
        int x0 = Mth.floor(x);
        int y0 = Mth.floor(y);
        int z0 = Mth.floor(z);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        float tx = x - x0;
        float ty = y - y0;
        float tz = z - z0;

        float fx = tx * tx * (3.0f - (2.0f * tx));
        float fy = ty * ty * (3.0f - (2.0f * ty));
        float fz = tz * tz * (3.0f - (2.0f * tz));

        float c000 = hash3ToUnit(x0, y0, z0);
        float c100 = hash3ToUnit(x1, y0, z0);
        float c010 = hash3ToUnit(x0, y1, z0);
        float c110 = hash3ToUnit(x1, y1, z0);
        float c001 = hash3ToUnit(x0, y0, z1);
        float c101 = hash3ToUnit(x1, y0, z1);
        float c011 = hash3ToUnit(x0, y1, z1);
        float c111 = hash3ToUnit(x1, y1, z1);

        float x00 = Mth.lerp(fx, c000, c100);
        float x10 = Mth.lerp(fx, c010, c110);
        float x01 = Mth.lerp(fx, c001, c101);
        float x11 = Mth.lerp(fx, c011, c111);
        float y0v = Mth.lerp(fy, x00, x10);
        float y1v = Mth.lerp(fy, x01, x11);
        return Mth.lerp(fz, y0v, y1v);
    }

    private static float hash3ToUnit(int x, int y, int z) {
        int h = x * 374761393;
        h = (h ^ (y * 668265263)) * 1274126177;
        h ^= z * 1442695041;
        h ^= (h >>> 13);
        h *= 1274126177;
        h ^= (h >>> 16);
        return (h & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }


    private static void quad(
        VertexConsumer vc,
        Matrix4f mat,
        Matrix3f normal,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3,
        float u0,
        float v0,
        float u1,
        float v1,
        int r,
        int g,
        int b,
        int a,
        float nx,
        float ny,
        float nz
    ) {
        vertex(vc, mat, normal, x0, y0, z0, u0, v0, r, g, b, a, nx, ny, nz);
        vertex(vc, mat, normal, x1, y1, z1, u0, v1, r, g, b, a, nx, ny, nz);
        vertex(vc, mat, normal, x2, y2, z2, u1, v1, r, g, b, a, nx, ny, nz);
        vertex(vc, mat, normal, x3, y3, z3, u1, v0, r, g, b, a, nx, ny, nz);
    }

    private static void quadGradient(
        VertexConsumer vc,
        Matrix4f mat,
        Matrix3f normal,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3,
        float u0,
        float v0,
        float u1,
        float v1,
        int r0,
        int g0,
        int b0,
        int a0,
        int r1,
        int g1,
        int b1,
        int a1,
        int r2,
        int g2,
        int b2,
        int a2,
        int r3,
        int g3,
        int b3,
        int a3,
        float nx,
        float ny,
        float nz
    ) {
        vertex(vc, mat, normal, x0, y0, z0, u0, v0, r0, g0, b0, a0, nx, ny, nz);
        vertex(vc, mat, normal, x1, y1, z1, u0, v1, r1, g1, b1, a1, nx, ny, nz);
        vertex(vc, mat, normal, x2, y2, z2, u1, v1, r2, g2, b2, a2, nx, ny, nz);
        vertex(vc, mat, normal, x3, y3, z3, u1, v0, r3, g3, b3, a3, nx, ny, nz);
    }

    private static void vertex(
        VertexConsumer vc,
        Matrix4f mat,
        Matrix3f normal,
        float x,
        float y,
        float z,
        float u,
        float v,
        int r,
        int g,
        int b,
        int a,
        float nx,
        float ny,
        float nz
    ) {
        vc.vertex(mat, x, y, z)
            .color(r, g, b, a)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(0x00F000F0)
            .normal(normal, nx, ny, nz)
            .endVertex();
    }

    private record LiveCube(
        Vec3 center,
        float halfExtent,
        float easedProgress,
        int baseColor,
        float pulse,
        @Nullable ShieldContactField field
    ) {
    }

    private static final class CubeState {
        private boolean targetActive;
        private float progress;
        private float halfExtent = 16.0f;
        private FrozenPigment pigment;
        private final float pulseSeed = (float) (Math.random() * 1000.0);
        @Nullable
        private ShieldContactField contactField;

        private ShieldContactField field() {
            if (contactField == null) {
                contactField = new ShieldContactField();
            }
            return contactField;
        }

        private void dispose() {
            if (contactField != null) {
                contactField.close();
                contactField = null;
            }
        }
    }
}
