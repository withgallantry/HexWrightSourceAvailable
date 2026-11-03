package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalCone;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public abstract class FrustumPortalMixin {

    @Inject(method = "cubeInFrustum(DDDDDD)Z", at = @At("RETURN"), cancellable = true)
    private void hexwright$cullOutsidePortalCone(double minX, double minY, double minZ,
                                                 double maxX, double maxY, double maxZ,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!PortalViewRenderer.isRenderingView() || !cir.getReturnValueZ()) {
            return;
        }
        PortalCone cone = PortalViewRenderer.activeCone();
        if (cone != null && cone.isOutside(minX, minY, minZ, maxX, maxY, maxZ)) {
            cir.setReturnValue(false);
        }
    }
}
