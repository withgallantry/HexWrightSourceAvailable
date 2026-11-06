package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.weapon.DivineStrainFog;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererDivineStrainMixin {

    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void hexwright$divineStrainFog(Camera camera, FogRenderer.FogMode mode, float farPlaneDistance,
                                                  boolean foggy, float partialTick, CallbackInfo ci) {
        float severity = DivineStrainFog.severity(camera);
        if (severity <= 0.0F) {
            return;
        }
        float end = DivineStrainFog.fogEnd(severity);
        boolean sky = mode == FogRenderer.FogMode.FOG_SKY;
        float shownEnd = sky ? end * 0.8F : end;
        if (RenderSystem.getShaderFogEnd() <= shownEnd) {
            return;
        }
        RenderSystem.setShaderFogStart(sky ? 0.0F : end * 0.25F);
        RenderSystem.setShaderFogEnd(shownEnd);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void hexwright$divineStrainColour(Camera camera, float partialTick, ClientLevel level,
                                                     int renderDistance, float bossColorModifier, CallbackInfo ci) {
        float severity = DivineStrainFog.severity(camera);
        if (severity <= 0.0F || camera.getFluidInCamera() == FogType.LAVA) {
            return;
        }
        float light = 1.0F - DivineStrainFog.darkness(severity);
        fogRed *= light;
        fogGreen *= light;
        fogBlue *= light;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }
}
