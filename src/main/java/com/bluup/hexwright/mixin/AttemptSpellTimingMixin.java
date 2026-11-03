package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import com.bluup.hexwright.server.region.RegionThothBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OperatorSideEffect.AttemptSpell.class)
public abstract class AttemptSpellTimingMixin {

    @Inject(method = "performEffect", at = @At("HEAD"), remap = false)
    private void hexwright$timeStart(CastingVM vm, CallbackInfo ci) {
        RegionThothBenchmark.SpellTiming.enter();
    }

    @Inject(method = "performEffect", at = @At("RETURN"), remap = false)
    private void hexwright$timeEnd(CastingVM vm, CallbackInfo ci) {
        RegionThothBenchmark.SpellTiming.exit();
    }
}
