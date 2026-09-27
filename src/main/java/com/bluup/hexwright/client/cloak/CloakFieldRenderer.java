package com.bluup.hexwright.client.cloak;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.client.render.SceneSnapshot;
import com.bluup.hexwright.server.cloak.CloakField;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class CloakFieldRenderer {

    private static final double MAX_DRAW_DISTANCE = 256.0;
    private static final int SEGMENTS = 48;
    private static final int RINGS = 16;
    private static final float SHIELD_FROM = 0.4f;
    private static final float SHIELD_FULL = 0.75f;

    private static final String[] RIPPLE_UNIFORMS = {"Ripple0", "Ripple1", "Ripple2", "Ripple3"};

    private static final List<CloakField> VISIBLE = new ArrayList<>();

    private CloakFieldRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(CloakFieldRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        List<CloakField> fields = ClientCloakFields.fields();
        if (fields.isEmpty()) {
            return;
        }
        ShaderInstance shader = CloakFieldShaders.shader();
        if (shader == null || PortalViewRenderer.isRenderingView() || RemoteLevelManager.isRemotePassActive()) {
            return;
        }

        Vec3 cameraPos = context.camera().getPosition();
        Frustum frustum = context.frustum();
        VISIBLE.clear();
        for (CloakField field : fields) {
            if (field.center().distanceTo(cameraPos) - Math.max(field.radius(), field.height()) > MAX_DRAW_DISTANCE) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(boundsOf(field))) {
                continue;
            }
            VISIBLE.add(field);
        }
        if (VISIBLE.isEmpty() || !SceneSnapshot.capture(SceneSnapshot.SLOT_CLOAK_FIELD)) {
            VISIBLE.clear();
            return;
        }
        VISIBLE.sort((a, b) -> Double.compare(
            b.center().distanceToSqr(cameraPos), a.center().distanceToSqr(cameraPos)));

        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f pose = poseStack.last().pose();

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        shader.setSampler("SceneSampler", SceneSnapshot.colorTextureId());
        shader.setSampler("DepthSampler", SceneSnapshot.depthTextureId());
        shader.safeGetUniform("DepthReady").set(SceneSnapshot.depthAvailable() ? 1.0f : 0.0f);
        boolean fast = Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
        shader.safeGetUniform("Fast").set(fast ? 1.0f : 0.0f);
        Vector3f worldUp = pose.transformDirection(0.0f, 1.0f, 0.0f, new Vector3f()).normalize();
        shader.safeGetUniform("WorldUp").set(worldUp.x, worldUp.y, worldUp.z);
        double halfScreenAngle = Math.atan(1.0 / context.projectionMatrix().m00());
        long now = System.nanoTime();

        for (CloakField field : VISIBLE) {
            Vec3 c = field.center();
            Vector3f viewCenter = pose.transformPosition((float) c.x, (float) c.y, (float) c.z, new Vector3f());
            shader.safeGetUniform("SphereCenter").set(viewCenter.x, viewCenter.y, viewCenter.z);
            shader.safeGetUniform("Radius").set(field.radius());
            shader.safeGetUniform("Stretch").set(field.radius() / field.height());
            shader.safeGetUniform("Inside").set(field.contains(cameraPos) ? 1.0f : 0.0f);
            shader.safeGetUniform("Seed").set(seedOf(field));
            shader.safeGetUniform("Proximity").set(proximity(field, cameraPos, halfScreenAngle));
            shader.safeGetUniform("Rise").set(ClientCloakFields.rise(field, now));
            setRipples(shader, field, pose, now);
            RenderSystem.setShader(() -> shader);
            drawDome(pose, field);
        }
        VISIBLE.clear();

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    private static void drawDome(Matrix4f pose, CloakField field) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int ring = 0; ring < RINGS; ring++) {
            float lowElevation = Mth.HALF_PI * ring / RINGS;
            float highElevation = Mth.HALF_PI * (ring + 1) / RINGS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float fromAzimuth = Mth.TWO_PI * segment / SEGMENTS;
                float toAzimuth = Mth.TWO_PI * (segment + 1) / SEGMENTS;
                float fromU = (float) segment / SEGMENTS;
                float toU = (float) (segment + 1) / SEGMENTS;
                float lowV = (float) ring / RINGS;
                float highV = (float) (ring + 1) / RINGS;
                domeVertex(builder, pose, field, fromAzimuth, lowElevation, fromU, lowV);
                domeVertex(builder, pose, field, toAzimuth, lowElevation, toU, lowV);
                domeVertex(builder, pose, field, toAzimuth, highElevation, toU, highV);
                domeVertex(builder, pose, field, fromAzimuth, highElevation, fromU, highV);
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void domeVertex(BufferBuilder builder, Matrix4f pose, CloakField field,
                                   float azimuth, float elevation, float u, float v) {
        Vec3 c = field.center();
        float across = Mth.cos(elevation) * field.radius();
        builder.vertex(pose,
                (float) c.x + Mth.cos(azimuth) * across,
                (float) c.y + Mth.sin(elevation) * field.height(),
                (float) c.z + Mth.sin(azimuth) * across)
            .uv(u, v)
            .endVertex();
    }

    private static float proximity(CloakField field, Vec3 cameraPos, double halfScreenAngle) {
        if (field.contains(cameraPos)) {
            return 1.0f;
        }
        Vec3 c = field.center();
        double axisY = Mth.clamp(cameraPos.y, c.y, c.y + field.height());
        double distance = cameraPos.distanceTo(new Vec3(c.x, axisY, c.z));
        double halfWidth = distance <= field.radius() ? Mth.HALF_PI : Math.asin(field.radius() / distance);
        float share = (float) (halfWidth / halfScreenAngle);
        float blend = Mth.clamp((share - SHIELD_FROM) / (SHIELD_FULL - SHIELD_FROM), 0.0f, 1.0f);
        return blend * blend * (3.0f - 2.0f * blend);
    }

    private static void setRipples(ShaderInstance shader, CloakField field, Matrix4f pose, long now) {
        int slot = 0;
        for (CloakFieldRipples.Ripple ripple : CloakFieldRipples.of(field)) {
            if (slot == CloakFieldRipples.MAX_PER_FIELD) {
                break;
            }
            Vec3 p = ripple.point();
            Vector3f view = pose.transformPosition((float) p.x, (float) p.y, (float) p.z, new Vector3f());
            shader.safeGetUniform(RIPPLE_UNIFORMS[slot++])
                .set(view.x, view.y, view.z, CloakFieldRipples.age(ripple, now));
        }
        while (slot < CloakFieldRipples.MAX_PER_FIELD) {
            shader.safeGetUniform(RIPPLE_UNIFORMS[slot++]).set(0.0f, 0.0f, 0.0f, -1.0f);
        }
    }

    private static AABB boundsOf(CloakField field) {
        Vec3 c = field.center();
        double r = field.radius();
        return new AABB(c.x - r, c.y, c.z - r, c.x + r, c.y + field.height(), c.z + r);
    }

    private static float seedOf(CloakField field) {
        return (field.id().getLeastSignificantBits() >>> 16 & 0xFFFFL) / 65535.0f * 64.0f;
    }
}
