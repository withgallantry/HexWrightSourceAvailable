package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.hexweave.WovenOrbClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class WovenOrbMouseMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void hexwright$reportButtonToWovenOrb(long windowPointer, int button, int action, int modifiers,
                                                  CallbackInfo ci) {
        WovenOrbClient.onInput(windowPointer, InputConstants.Type.MOUSE.getOrCreate(button), action);
    }
}
