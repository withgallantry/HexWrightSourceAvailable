package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.hexicon.HexiconOverlay;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class HexiconKeyPressMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void hexwright$consumeNumberKeysForHexicon(long windowPointer, int keyCode, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (HexiconOverlay.onNumberKeyPress(keyCode, action)) {
            ci.cancel();
        }
    }
}
