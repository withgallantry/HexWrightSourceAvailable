package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.render.HexwrightIrisPipeline;
import com.bluup.hexwright.client.render.IrisCompat;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public class IrisPipelineIsolationMixin implements HexwrightIrisPipeline {

    @Shadow
    public boolean isBeforeTranslucent;

    @Shadow
    private boolean isRenderingWorld;

    @Override
    public void hexwright$setBeforeTranslucent(boolean beforeTranslucent) {
        this.isBeforeTranslucent = beforeTranslucent;
    }

    @Override
    public void hexwright$setRenderingWorld(boolean renderingWorld) {
        this.isRenderingWorld = renderingWorld;
    }



    @Unique
    private static boolean hexwright$packStandsDown() {
        return IrisCompat.isPrivatePassActive() || PortalViewRenderer.isRenderingView();
    }

    @Inject(method = "shouldOverrideShaders", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hexwright$vanillaShadersForPrivatePasses(CallbackInfoReturnable<Boolean> cir) {
        if (hexwright$packStandsDown()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = {"beginLevelRendering", "beginTranslucents", "finalizeLevelRendering",
        "finalizeGameRendering", "renderShadows"},
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hexwright$skipPackStagesInPortalPass(CallbackInfo ci) {
        if (PortalViewRenderer.isRenderingView()) {
            ci.cancel();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getPhase", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hexwright$noPhaseDuringPrivatePasses(CallbackInfoReturnable cir) {
        if (!hexwright$packStandsDown()) {
            return;
        }
        Object none = IrisCompat.worldRenderingPhaseNone();
        if (none != null) {
            cir.setReturnValue(none);
        }
    }
}
