package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.lowdragmc.photon.client.postprocessing.BloomEffect;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BloomEffect.class, remap = false)
public abstract class BloomEffectPortalMixin {

    @Inject(method = "getInput", at = @At("HEAD"), cancellable = true)
    private static void hexwright$paneTargetInsteadOfBloomInput(CallbackInfoReturnable<RenderTarget> cir) {
        if (PortalViewRenderer.isRenderingView() && !PortalViewRenderer.PANE_BLOOM) {
            cir.setReturnValue(Minecraft.getInstance().getMainRenderTarget());
        }
    }
}
