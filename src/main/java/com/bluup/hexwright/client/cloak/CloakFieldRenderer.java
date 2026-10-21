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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class CloakFieldRenderer {

    private static final double MAX_DRAW_DISTANCE = 256.0;
    private static final float WARD_INSIDE = 0.5f;
    private static final float WARD_AFTER_RISE = 0.85f;

    private static final String[] RIPPLE_UNIFORMS = {"Ripple0", "Ripple1", "Ripple2", "Ripple3"};

    private record Visible(CloakField field, boolean inside, List<CloakFieldWard.Face> faces) {
    }

    private static final List<Visible> VISIBLE = new ArrayList<>();

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
            if (field.surfacePoint(cameraPos).distanceTo(cameraPos) > MAX_DRAW_DISTANCE) {
                continue;
            }
            boolean inside = field.contains(cameraPos);
            List<CloakFieldWard.Face> faces = facesInView(field, cameraPos, inside, frustum);
            if (!faces.isEmpty()) {
                VISIBLE.add(new Visible(field, inside, faces));
            }
        }
        if (VISIBLE.isEmpty() || !SceneSnapshot.capture(SceneSnapshot.SLOT_CLOAK_FIELD)) {
            VISIBLE.clear();
            return;
        }
        VISIBLE.sort((a, b) -> Double.compare(
            b.field().center().distanceToSqr(cameraPos), a.field().center().distanceToSqr(cameraPos)));

        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f pose = poseStack.last().pose();

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
        setAxis(shader, "WorldEast", pose, 1.0f, 0.0f, 0.0f);
        setAxis(shader, "WorldUp", pose, 0.0f, 1.0f, 0.0f);
        setAxis(shader, "WorldSouth", pose, 0.0f, 0.0f, 1.0f);
        long now = System.nanoTime();

        for (Visible visible : VISIBLE) {
            CloakField field = visible.field();
            Vec3 c = field.center();
            boolean inside = visible.inside();
            float rise = ClientCloakFields.rise(field, now);
            Vector3f viewCenter = pose.transformPosition((float) c.x, (float) c.y, (float) c.z, new Vector3f());
            shader.safeGetUniform("BoxBase").set(viewCenter.x, viewCenter.y, viewCenter.z);
            shader.safeGetUniform("HalfWidth").set(field.halfWidth());
            shader.safeGetUniform("Height").set(field.height());
            shader.safeGetUniform("Inside").set(inside ? 1.0f : 0.0f);
            shader.safeGetUniform("Seed").set(seedOf(field));
            shader.safeGetUniform("Rise").set(rise);
            setRipples(shader, field, pose, now);
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> shader);
            drawBox(pose, visible.faces());

            float settled = Mth.clamp((rise - WARD_AFTER_RISE) / (1.0f - WARD_AFTER_RISE), 0.0f, 1.0f);
            CloakFieldWard.draw(pose, field, visible.faces(), frustum, cameraPos,
                (inside ? WARD_INSIDE : 1.0f) * settled, inside, fast, now / 1.0e9);
        }
        VISIBLE.clear();

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    private static List<CloakFieldWard.Face> facesInView(CloakField field, Vec3 cameraPos, boolean inside,
                                                         Frustum frustum) {
        Vector3f eye = new Vector3f((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        List<CloakFieldWard.Face> faces = new ArrayList<>(5);
        for (CloakFieldWard.Face face : CloakFieldWard.facesOf(field)) {
            if (!inside && eye.sub(face.middle(), new Vector3f()).dot(face.normal()) <= 0.0f) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(face.bounds())) {
                continue;
            }
            faces.add(face);
        }
        return faces;
    }

    private static void drawBox(Matrix4f pose, List<CloakFieldWard.Face> faces) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (CloakFieldWard.Face face : faces) {
            vertex(builder, pose, face.corner(-1.0f, -1.0f));
            vertex(builder, pose, face.corner(1.0f, -1.0f));
            vertex(builder, pose, face.corner(1.0f, 1.0f));
            vertex(builder, pose, face.corner(-1.0f, 1.0f));
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Matrix4f pose, Vector3f corner) {
        builder.vertex(pose, corner.x, corner.y, corner.z).endVertex();
    }

    private static void setAxis(ShaderInstance shader, String name, Matrix4f pose, float x, float y, float z) {
        Vector3f axis = pose.transformDirection(x, y, z, new Vector3f()).normalize();
        shader.safeGetUniform(name).set(axis.x, axis.y, axis.z);
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

        CloakFieldRipples.Swell swell = CloakFieldRipples.swell(field, now);
        if (swell == null) {
            shader.safeGetUniform("Swell").set(0.0f, 0.0f, 0.0f, -1.0f);
        } else {
            Vec3 p = swell.point();
            Vector3f view = pose.transformPosition((float) p.x, (float) p.y, (float) p.z, new Vector3f());
            shader.safeGetUniform("Swell").set(view.x, view.y, view.z, swell.age());
        }
    }

    private static float seedOf(CloakField field) {
        return (field.id().getLeastSignificantBits() >>> 16 & 0xFFFFL) / 65535.0f * 64.0f;
    }
}
