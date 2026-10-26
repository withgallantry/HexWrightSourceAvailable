package com.bluup.hexwright.client.flourish;

import at.petrak.hexcasting.client.ClientTickCounter;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class FlourishRenderer {

    private static final String[] COLOUR_UNIFORMS = {"Color0", "Color1", "Color2", "Color3"};

    private FlourishRenderer() {
    }

    public static void register() {
        WorldRenderEvents.LAST.register(FlourishRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        ShaderInstance shader = FlourishShaders.shader();
        if (shader == null || RemoteLevelManager.isRemotePassActive()) {
            return;
        }
        List<ClientFlourishes.Live> live = ClientFlourishes.live();
        if (live.isEmpty()) {
            return;
        }

        FlourishPen pen = new FlourishPen(shader, context.matrixStack().last().pose(), context.camera(),
            context.tickDelta());
        Vec3 cameraPos = pen.cameraPos();
        float time = ClientTickCounter.getTotal();
        long now = System.nanoTime();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        for (ClientFlourishes.Live flourish : live) {
            if (flourish.cast.pos().distanceToSqr(cameraPos) > ClientFlourishes.MAX_DRAW_DISTANCE_SQ) {
                continue;
            }
            float ticks = flourish.ticks(now);
            float progress = ticks / flourish.look.lifetimeTicks();
            if (progress < 0.0f || progress >= 1.0f) {
                continue;
            }
            shader.safeGetUniform("Age").set(ticks / 20.0f);
            shader.safeGetUniform("Seed").set(flourish.seed());
            shader.safeGetUniform("Intensity").set(1.0f);
            for (int i = 0; i < COLOUR_UNIFORMS.length; i++) {
                int rgb = flourish.colour(i, time);
                shader.safeGetUniform(COLOUR_UNIFORMS[i]).set(
                    (rgb >> 16 & 0xFF) / 255.0f, (rgb >> 8 & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
            }
            flourish.look.draw(pen, progress);
        }

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
