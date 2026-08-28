package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.client.render.SceneSnapshot;
import com.bluup.hexwright.server.portal.PortalPair;
import com.bluup.hexwright.server.portal.PortalWindow;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class PortalPlaneRenderer {

    private static final double MAX_DRAW_DISTANCE_SQ = 96.0 * 96.0;

    private static final double CENTER_LIFT = 0.004;

    private static final double NEAR_PLANE_COVER = 0.15;

    private static final float SETTLED_PROGRESS = 0.999f;

    private PortalPlaneRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> render(context, true));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> render(context, false));
    }

    private static void render(WorldRenderContext context, boolean settledPhase) {
        Minecraft mc = Minecraft.getInstance();
        List<ClientPortalManager.Entry> entries = ClientPortalManager.entries();
        if (mc.level == null || entries.isEmpty()) {
            return;
        }
        ShaderInstance shader = PortalShaders.shader();
        if (shader == null) {
            return;
        }

        if (RemoteLevelManager.isRemotePassActive()) {
            return;
        }

        Vec3 cameraPos = context.camera().getPosition();
        float partialTick = context.tickDelta();
        boolean inPortalPass = PortalViewRenderer.isRenderingView();

        PortalViewRenderer.ViewKey skipPane = PortalViewRenderer.activeDestinationPane();

        record Pane(ClientPortalManager.Entry entry, int side, double distSq,
                    float progress, int textureId) {
        }
        List<Pane> panes = new ArrayList<>();
        for (ClientPortalManager.Entry entry : entries) {
            for (int side = 0; side < 2; side++) {
                if (!ClientPortalManager.sideIsLocal(entry.pair(), side)) {
                    continue;
                }
                if (skipPane != null && skipPane.pairId().equals(entry.pair().id()) && skipPane.side() == side) {
                    continue;
                }
                double distSq = entry.pair().window(side).center().distanceToSqr(cameraPos);
                if (distSq > MAX_DRAW_DISTANCE_SQ) {
                    continue;
                }
                float progress = ClientPortalManager.openProgress(entry, partialTick);
                if (progress <= 0.0f) {
                    continue;
                }
                int textureId = inPortalPass ? -1 : PortalViewRenderer.viewTextureId(entry.pair().id(), side);
                boolean settled = progress >= SETTLED_PROGRESS && textureId >= 0;
                if (settled == settledPhase) {
                    panes.add(new Pane(entry, side, distSq, progress, textureId));
                }
            }
        }
        if (panes.isEmpty()) {
            return;
        }
        panes.sort((a, b) -> Double.compare(b.distSq(), a.distSq()));

        int sceneTextureId = -1;
        if (!settledPhase && !inPortalPass && SceneSnapshot.capture(SceneSnapshot.SLOT_PORTAL_PANE)) {
            sceneTextureId = SceneSnapshot.colorTextureId();
        }

        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f pose = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        for (Pane pane : panes) {
            PortalPair pair = pane.entry().pair();
            PortalWindow window = pair.window(pane.side());
            float progress = pane.progress();
            int textureId = pane.textureId();

            shader.safeGetUniform("WindowSize").set((float) window.width(), (float) window.height());
            shader.safeGetUniform("Progress").set(progress);
            shader.safeGetUniform("ViewReady").set(textureId >= 0 ? 1.0f : 0.0f);
            shader.safeGetUniform("SceneReady").set(sceneTextureId >= 0 ? 1.0f : 0.0f);
            shader.setSampler("PortalSampler", Math.max(textureId, 0));
            shader.setSampler("SceneSampler", Math.max(sceneTextureId, 0));
            RenderSystem.setShader(() -> shader);

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float w = (float) window.width();
            float h = (float) window.height();
            double camDepth = window.signedDistance(cameraPos);
            double towardViewer = Math.signum(camDepth);
            if (towardViewer == 0.0) {
                towardViewer = 1.0;
            }
            double offset = CENTER_LIFT;
            double distance = Math.abs(camDepth);
            if (distance < NEAR_PLANE_COVER && window.containsProjected(cameraPos, 0.0)) {
                offset = -(NEAR_PLANE_COVER - distance);
            }
            Vec3 paneOffset = window.normal().scale(offset * towardViewer);
            quad(builder, pose, window, paneOffset, 0.0f, 0.0f, w, h);
            BufferUploader.drawWithShader(builder.end());
        }

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void quad(BufferBuilder builder, Matrix4f pose, PortalWindow window, Vec3 offset,
                             float a0, float b0, float a1, float b1) {
        quadVertex(builder, pose, window, offset, a0, b0);
        quadVertex(builder, pose, window, offset, a1, b0);
        quadVertex(builder, pose, window, offset, a1, b1);
        quadVertex(builder, pose, window, offset, a0, b1);
    }

    private static void quadVertex(BufferBuilder builder, Matrix4f pose, PortalWindow window, Vec3 offset,
                                   float a, float b) {
        Vec3 point = window.pointAt(a, b).add(offset);
        builder.vertex(pose, (float) point.x, (float) point.y, (float) point.z).uv(a, b).endVertex();
    }
}
