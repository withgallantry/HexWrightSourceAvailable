package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.photon.PhotonSceneTextures;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CustomShaderMaterial.class, remap = false)
public abstract class PhotonSceneSamplerMixin {

    @Inject(method = "setupUniform", at = @At("RETURN"))
    private void hexwright$bindSceneSamplers(CallbackInfo ci) {
        PhotonSceneTextures.onCustomShaderSetup((CustomShaderMaterial) (Object) this);
    }
}
