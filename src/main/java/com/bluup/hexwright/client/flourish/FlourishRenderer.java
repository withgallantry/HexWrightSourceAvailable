package com.bluup.hexwright.client.flourish;

import at.petrak.hexcasting.client.ClientTickCounter;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class FlourishRenderer {

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

        BlendMode previousBlend = BlendModeAccessor.hexwright$getLastApplied();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        for (ClientFlourishes.Live flourish : live) {
            if (flourish.pos.distanceToSqr(cameraPos) > ClientFlourishes.MAX_DRAW_DISTANCE_SQ) {
                continue;
            }
            float ticks = flourish.ticks(now);
            float progress = ticks / flourish.look.lifetimeTicks();
            if (progress < 0.0f || progress >= 1.0f) {
                continue;
            }
            pen.flourish(ticks / 20.0f, flourish.seed(), 1.0f);
            pen.palette(index -> flourish.colour(index, time));
            flourish.look.draw(pen, progress);
        }

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        BlendModeAccessor.hexwright$setLastApplied(previousBlend);
    }
}
