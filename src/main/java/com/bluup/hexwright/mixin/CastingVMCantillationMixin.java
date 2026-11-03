package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import com.bluup.hexwright.server.cantillation.CastCantillations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = CastingVM.class, remap = false)
public abstract class CastingVMCantillationMixin {

    @ModifyArg(method = "queueExecuteAndWrapIotas", at = @At(value = "INVOKE",
        target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;postExecution(Lat/petrak/hexcasting/api/casting/eval/CastResult;)V"))
    private CastResult hexwright$cantillate(CastResult result) {
        return CastCantillations.hear(((CastingVM) (Object) this).getEnv(), result);
    }
}
