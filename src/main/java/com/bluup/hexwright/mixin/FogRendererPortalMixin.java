package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererPortalMixin {

    private static final float FOG_START_FRACTION = 0.55f;

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void hexwright$fogRemoteViewToStreamedExtent(Camera camera, FogRenderer.FogMode mode,
                                                                float farPlaneDistance, boolean foggy,
                                                                float partialTick, CallbackInfo ci) {
        if (!RemoteLevelManager.isRemotePassActive() || mode != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        double end = RemoteLevelManager.remoteFogEnd(camera.getPosition());
        if (end <= 0.0 || RenderSystem.getShaderFogEnd() <= (float) end) {
            return;
        }
        RenderSystem.setShaderFogStart((float) end * FOG_START_FRACTION);
        RenderSystem.setShaderFogEnd((float) end);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
