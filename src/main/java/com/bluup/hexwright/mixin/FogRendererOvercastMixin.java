package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.sky.Overcast;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererOvercastMixin {

    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void hexwright$overcastFog(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                              float bossColorModifier, CallbackInfo ci) {
        if (!Overcast.any(partialTick) || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        Vec3 fog = Overcast.fog(new Vec3(fogRed, fogGreen, fogBlue), partialTick);
        fogRed = (float) fog.x;
        fogGreen = (float) fog.y;
        fogBlue = (float) fog.z;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }
}
