package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.pentabox.PentaboxOverlay;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class PentaboxMouseMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void hexwright$captureForOverlay(CallbackInfo ci) {
        if (!PentaboxOverlay.isActive()) {
            return;
        }
        PentaboxOverlay.accumulateDrag(this.accumulatedDX, this.accumulatedDY);
        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;
        ci.cancel();
    }
}
