package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.particle.SubtleEffectsCompat;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineSubtleEffectsMixin {

    @Inject(method = "add", at = @At("HEAD"))
    private void hexwright$exemptPhotonFromSubtleEffectsCulling(Particle particle, CallbackInfo ci) {
        if (SubtleEffectsCompat.isPresent() && particle instanceof IFXObject) {
            SubtleEffectsCompat.exemptFromCulling(particle);
        }
    }
}
