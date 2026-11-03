package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.hexicon.HexiconOverlay;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class HexiconMouseMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void hexwright$captureForHexicon(CallbackInfo ci) {
        if (!HexiconOverlay.isActive()) {
            return;
        }
        HexiconOverlay.accumulateDrag(this.accumulatedDX, this.accumulatedDY);
        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;
        ci.cancel();
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void hexwright$consumeButtonsForHexicon(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        if (HexiconOverlay.onMouseButton(button, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void hexwright$consumeScrollForHexicon(long windowPointer, double horizontalOffset, double verticalOffset, CallbackInfo ci) {
        if (HexiconOverlay.onMouseScroll(verticalOffset)) {
            ci.cancel();
        }
    }
}
