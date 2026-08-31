package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import com.bluup.hexwright.server.staff_assembly.StaffCastFlare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OperatorSideEffect.AttemptSpell.class)
public abstract class AttemptSpellFlareMixin {

    @Inject(method = "performEffect", at = @At("HEAD"), remap = false)
    private void hexwright$flareOnSpell(CastingVM vm, CallbackInfo ci) {
        StaffCastFlare.onSpellPerformed(vm.getEnv());
    }
}
