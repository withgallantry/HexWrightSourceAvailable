package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.render.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public class IrisDimensionPinMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getCurrentDimension", at = @At("HEAD"), cancellable = true,
        remap = false, require = 0)
    private static void hexwright$pinDimensionDuringPortalPass(CallbackInfoReturnable cir) {
        if (!PortalViewRenderer.isRenderingView()) {
            return;
        }
        Object pinned = IrisCompat.lastDimension();
        if (pinned != null) {
            cir.setReturnValue(pinned);
        }
    }
}
