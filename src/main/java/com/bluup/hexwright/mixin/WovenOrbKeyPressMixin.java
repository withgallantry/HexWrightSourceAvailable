package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.hexweave.WovenOrbClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class WovenOrbKeyPressMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void hexwright$reportKeyToWovenOrb(long windowPointer, int keyCode, int scanCode, int action, int modifiers,
                                                CallbackInfo ci) {
        WovenOrbClient.onInput(windowPointer, InputConstants.getKey(keyCode, scanCode), action);
    }
}
