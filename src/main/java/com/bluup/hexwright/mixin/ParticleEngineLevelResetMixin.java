package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.block.AlembixVesselVisualClient;
import com.bluup.hexwright.client.block.CrucibleFlameVisualClient;
import com.bluup.hexwright.client.block.ManifoldVaultVisualClient;
import com.bluup.hexwright.client.block.ResonanceTowerVisualClient;
import com.bluup.hexwright.client.wardingbox.WardingBoxHitVisualClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineLevelResetMixin {

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void hexwright$forgetPhotonEffectCaches(ClientLevel level, CallbackInfo ci) {
        AlembixVesselVisualClient.onParticlesCleared();
        CrucibleFlameVisualClient.onParticlesCleared();
        ManifoldVaultVisualClient.onParticlesCleared();
        ResonanceTowerVisualClient.onParticlesCleared();
        WardingBoxHitVisualClient.onParticlesCleared();
        com.bluup.hexwright.client.weapon.WeaponTrailVisualClient.onParticlesCleared();
        com.bluup.hexwright.client.dust.DustClient.onParticlesCleared();
    }
}
