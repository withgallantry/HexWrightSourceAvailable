package com.bluup.hexwright.client.inversion;

import at.petrak.hexcasting.client.ClientTickCounter;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.hexahedron.ClientHexahedra;
import com.bluup.hexwright.client.hexahedron.HexahedronRenderer;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.server.inversion.Inversion;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class InversionSkyRenderer {

    private static final float FLARE_TICKS = 30.0f;
    private static final double UNDER_RIFT = 4.0;
    private static final ResourceLocation PHASE = Hexwright.id("inversion_sky");
    private static final float MARGIN = 200.0f;
    private static final double SEEN_FROM = 2000.0;
    private static final double FADES_FROM = 1500.0;
    private static final float FAR_SHARE = 0.45f;
    private static final float TIME_WRAP_TICKS = 24000.0f;

    private InversionSkyRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_SETUP.register(PHASE, InversionSkyRenderer::render);
        WorldRenderEvents.AFTER_SETUP.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
        WorldRenderEvents.AFTER_SETUP.addPhaseOrdering(PHASE, HexahedronRenderer.CUBE_PHASE);
    }

    private static void render(WorldRenderContext context) {
        ClientLevel level = context.world();
        List<Inversion> inversions = ClientInversions.in(level);
        if (inversions.isEmpty() || !InversionShaders.ready() || IrisCompat.isRenderingShadowPass()
            || PortalViewRenderer.isRenderingView() || RemoteLevelManager.isRemotePassActive()
            || HexahedronRenderer.skyHidden(context.camera())) {
            return;
        }
        Vec3 eye = context.camera().getPosition();
        double gameTime = level.getGameTime() + context.tickDelta();
        float time = (ClientTickCounter.getTotal() % TIME_WRAP_TICKS) / 20.0f;
        float far = Minecraft.getInstance().gameRenderer.getDepthFar();

        BlendMode previousBlend = BlendModeAccessor.hexwright$getLastApplied();
        BlendModeAccessor.hexwright$setLastApplied(null);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(context.matrixStack().last().pose());
        RenderSystem.applyModelViewMatrix();

        ShaderInstance shader = InversionShaders.sky();
        for (Inversion inversion : inversions) {
            double across = Math.hypot(eye.x - inversion.x(), eye.z - inversion.z());
            if (across >= SEEN_FROM) {
                continue;
            }
            float fade = (float) Mth.clamp((SEEN_FROM - across) / (SEEN_FROM - FADES_FROM), 0.0, 1.0);
            double skyY = skyY(level, inversion, context.tickDelta());
            Vector3f camera = new Vector3f((float) (eye.x - inversion.x()), (float) (eye.y - skyY),
                (float) (eye.z - inversion.z()));
            float outer = Inversion.HALF + MARGIN;
            float reach = 0.0f;
            for (int corner = 0; corner < 4; corner++) {
                reach = Math.max(reach, camera.distance(corner < 2 ? -outer : outer, 0.0f, corner % 2 == 0 ? -outer : outer));
            }
            float scale = Math.max(1.0f, reach / (FAR_SHARE * far));

            shader.safeGetUniform("Scale").set(scale);
            shader.safeGetUniform("CameraOffset").set(camera);
            shader.safeGetUniform("Time").set(time);
            shader.safeGetUniform("Front").set((float) inversion.front(gameTime));
            shader.safeGetUniform("Half").set((float) Inversion.HALF);
            shader.safeGetUniform("Healing").set(inversion.phase() == Inversion.Phase.RECEDING ? 1.0f : 0.0f);
            shader.safeGetUniform("Fade").set(fade);
            shader.safeGetUniform("Angle").set(inversion.rentAngle());
            shader.safeGetUniform("Flare").set(inversion.phase() == Inversion.Phase.HELD
                ? (float) Math.exp(-(gameTime - inversion.since()) / FLARE_TICKS) : 0.0f);

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            vertex(builder, -outer, -outer, camera, scale);
            vertex(builder, outer, -outer, camera, scale);
            vertex(builder, outer, outer, camera, scale);
            vertex(builder, -outer, outer, camera, scale);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(builder.end());
        }

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        BlendModeAccessor.hexwright$setLastApplied(previousBlend);
    }

    private static double skyY(ClientLevel level, Inversion inversion, float partialTick) {
        Vec3 middle = new Vec3(inversion.x(), inversion.groundY(), inversion.z());
        for (ClientHexahedra.Shown shown : ClientHexahedra.shown(level, partialTick)) {
            if (shown.hexahedron().covers(middle)) {
                return shown.hexahedron().riftY() - UNDER_RIFT;
            }
        }
        return inversion.rentY(level.getMaxBuildHeight());
    }

    private static void vertex(BufferBuilder builder, float x, float z, Vector3f camera, float scale) {
        builder.vertex((x - camera.x) / scale, -camera.y / scale, (z - camera.z) / scale).endVertex();
    }
}
