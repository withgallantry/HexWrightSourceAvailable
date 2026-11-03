package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import com.bluup.hexwright.server.flourish.CastFlourishes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OperatorSideEffect.Particles.class)
public abstract class ParticlesSideEffectFlourishMixin {

    @Inject(method = "performEffect", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexwright$flourish(CastingVM vm, CallbackInfo ci) {
        if (CastFlourishes.replaces(vm.getEnv(), ((OperatorSideEffect.Particles) (Object) this).getSpray())) {
            ci.cancel();
        }
    }
}
