package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.hexicon.HexiconOverlay;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class HexiconScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void hexwright$consumeScrollForHexicon(long windowPointer, double horizontalOffset, double verticalOffset, CallbackInfo ci) {
        if (HexiconOverlay.onMouseScroll(verticalOffset)) {
            ci.cancel();
        }
    }
}
