package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.render.IrisCompat;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class IrisPipelineIsolationMixin {
    @Inject(method = "shouldOverrideShaders", at = @At("HEAD"), cancellable = true)
    private void hexwright$privateShaders(CallbackInfoReturnable<Boolean> cir) {
        if (IrisCompat.isPrivatePassActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getPhase", at = @At("HEAD"), cancellable = true)
    private void hexwright$privatePhase(CallbackInfoReturnable<WorldRenderingPhase> cir) {
        if (IrisCompat.isPrivatePassActive()) {
            cir.setReturnValue(WorldRenderingPhase.NONE);
        }
    }
}
