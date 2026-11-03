package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.lowdragmc.photon.client.gameobject.emitter.PhotonParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PhotonParticleRenderType.class, remap = false)
public abstract class PhotonPortalPassMixin {

    @Inject(method = "renderBloom", at = @At("HEAD"), cancellable = true)
    private static void hexwright$unscissorBloomComposite(CallbackInfo ci) {
        PortalViewRenderer.debugBloomAttachment();
        if (PortalViewRenderer.isRenderingView() && !PortalViewRenderer.PANE_BLOOM) {
            ci.cancel();
            return;
        }
        PortalViewRenderer.suspendScissor();
    }

    @Inject(method = "renderBloom", at = @At("RETURN"))
    private static void hexwright$restoreBloomScissor(CallbackInfo ci) {
        PortalViewRenderer.resumeScissor();
    }
}
