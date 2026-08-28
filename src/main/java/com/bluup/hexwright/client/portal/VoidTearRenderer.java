package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.client.render.SceneSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class VoidTearRenderer {

    public static final double MAX_DRAW_DISTANCE_SQ = 64.0 * 64.0;

    private static final float LONG_MARGIN = 1.35f;

    private static final float ACROSS_MARGIN = 2.4f;

    public record Rift(Vec3 center, Vec3 u, Vec3 v, float seed, float progress) {

        Vec3 pointAt(double a, double b) {
            return center.add(u.scale(a)).add(v.scale(b));
        }
    }

    public interface Source {
        void collect(Vec3 cameraPos, List<Rift> into);
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    private static final List<Rift> SCRATCH = new ArrayList<>();

    private VoidTearRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(VoidTearRenderer::render);
    }

    public static void addSource(Source source) {
        SOURCES.add(source);
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SOURCES.isEmpty()) {
            return;
        }
        ShaderInstance shader = VoidTearShaders.shader();
        if (shader == null) {
            return;
        }
        if (RemoteLevelManager.isRemotePassActive()) {
            return;
        }

        Vec3 cameraPos = context.camera().getPosition();
        SCRATCH.clear();
        for (Source source : SOURCES) {
            source.collect(cameraPos, SCRATCH);
        }
        Frustum frustum = context.frustum();
        if (frustum != null) {
            SCRATCH.removeIf(rift -> !frustum.isVisible(boundsOf(rift)));
        }
        if (SCRATCH.isEmpty()) {
            return;
        }
        SCRATCH.sort((a, b) -> Double.compare(
            b.center().distanceToSqr(cameraPos), a.center().distanceToSqr(cameraPos)));

        int sceneTextureId = -1;
        if (!PortalViewRenderer.isRenderingView() && SceneSnapshot.capture(SceneSnapshot.SLOT_VOID_TEAR)) {
            sceneTextureId = SceneSnapshot.colorTextureId();
        }

        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f pose = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        for (Rift rift : SCRATCH) {
            float halfLength = (float) rift.u().length();
            float halfAperture = (float) rift.v().length();

            shader.safeGetUniform("TearSize").set(halfLength, halfAperture);
            shader.safeGetUniform("Progress").set(rift.progress());
            shader.safeGetUniform("Seed").set(rift.seed());
            shader.safeGetUniform("SceneReady").set(sceneTextureId >= 0 ? 1.0f : 0.0f);
            shader.setSampler("SceneSampler", Math.max(sceneTextureId, 0));
            RenderSystem.setShader(() -> shader);

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            quadVertex(builder, pose, rift, -LONG_MARGIN, -ACROSS_MARGIN, halfLength, halfAperture);
            quadVertex(builder, pose, rift, LONG_MARGIN, -ACROSS_MARGIN, halfLength, halfAperture);
            quadVertex(builder, pose, rift, LONG_MARGIN, ACROSS_MARGIN, halfLength, halfAperture);
            quadVertex(builder, pose, rift, -LONG_MARGIN, ACROSS_MARGIN, halfLength, halfAperture);
            BufferUploader.drawWithShader(builder.end());
        }
        SCRATCH.clear();

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static AABB boundsOf(Rift rift) {
        double reach = rift.u().length() * LONG_MARGIN + rift.v().length() * ACROSS_MARGIN;
        return new AABB(rift.center().subtract(reach, reach, reach),
            rift.center().add(reach, reach, reach));
    }

    private static void quadVertex(BufferBuilder builder, Matrix4f pose, Rift rift,
                                   float a, float b, float halfLength, float halfAperture) {
        Vec3 point = rift.pointAt(a, b);
        builder.vertex(pose, (float) point.x, (float) point.y, (float) point.z)
            .uv(a * halfLength, b * halfAperture)
            .endVertex();
    }
}
