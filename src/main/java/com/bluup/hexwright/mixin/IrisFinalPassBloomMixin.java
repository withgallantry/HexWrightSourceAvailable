package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.client.portal.PortalPlaneRenderer;
import com.bluup.hexwright.client.render.emissive.EmissiveBloom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.FinalPassRenderer", remap = false)
public class IrisFinalPassBloomMixin {


    @Inject(method = "renderFinalPass", at = @At("TAIL"), remap = false, require = 0)
    private void hexwright$compositeEmissiveBloom(CallbackInfo ci) {
        IrisCompat.noteFinalPassHook();
        PortalPlaneRenderer.onIrisFinalPassComplete();
        EmissiveBloom.onIrisFinalPassComplete();
    }
}
