package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.sky.ClientSkies;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererCellSkyMixin {

    @Shadow
    private @Nullable ClientLevel level;

    @Inject(
        method = "renderSky",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;skyType()Lnet/minecraft/client/renderer/DimensionSpecialEffects$SkyType;",
            ordinal = 0
        ),
        cancellable = true
    )
    private void hexwright$cellSky(PoseStack poseStack, Matrix4f projection, float partialTick, Camera camera,
                                   boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        Vec3 eye = camera.getPosition();
        if (ClientSkies.render(poseStack, this.level, eye.x, eye.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void hexwright$noCloudsUnderCellSky(PoseStack poseStack, Matrix4f projection, float partialTick,
                                                double camX, double camY, double camZ, CallbackInfo ci) {
        if (ClientSkies.covers(this.level, camX, camZ)) {
            ci.cancel();
        }
    }
}
