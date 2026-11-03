package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.client.portal.RemotePhotonVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineRemotePassMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hexwright$noForeignParticlesInRemotePass(PoseStack poseStack,
                                                          MultiBufferSource.BufferSource bufferSource,
                                                          LightTexture lightTexture,
                                                          Camera camera, float partialTick,
                                                          CallbackInfo ci) {
        if (!RemoteLevelManager.isRemotePassActive()) {
            return;
        }
        RemotePhotonVisuals.render(poseStack, camera, partialTick);
        ci.cancel();
    }
}
