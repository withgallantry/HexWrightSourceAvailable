package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftShaderTransparencyMixin {

    @Inject(method = "useShaderTransparency", at = @At("HEAD"), cancellable = true)
    private static void hexwright$fancyPathInsidePortalPass(CallbackInfoReturnable<Boolean> cir) {
        if (PortalViewRenderer.isRenderingView() && PortalViewRenderer.FABULOUS_VIEWS) {
            cir.setReturnValue(false);
        }
    }
}
