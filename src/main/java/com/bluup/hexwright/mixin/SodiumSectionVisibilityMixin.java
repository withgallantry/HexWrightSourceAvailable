package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public class SodiumSectionVisibilityMixin {

    @Inject(method = "isSectionVisible", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexwright$everythingVisibleInPortalPass(int x, int y, int z,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (PortalViewRenderer.isRenderingView()) {
            cir.setReturnValue(true);
        }
    }
}
