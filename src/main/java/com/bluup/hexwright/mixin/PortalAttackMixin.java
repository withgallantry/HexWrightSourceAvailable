package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalAttackHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class PortalAttackMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void hexwright$attackThroughPortal(CallbackInfoReturnable<Boolean> cir) {
        if (PortalAttackHandler.startAttack((Minecraft) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void hexwright$mineThroughPortal(boolean holding, CallbackInfo ci) {
        if (PortalAttackHandler.continueAttack((Minecraft) (Object) this, holding)) {
            ci.cancel();
        }
    }
}
