package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import com.bluup.hexwright.server.constructshand.ConstructsGrasp;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StaffCastEnv.class, remap = false)
public abstract class StaffCastEnvConstructsGraspMixin {

    @WrapOperation(
        method = "handleNewPatternOnServer",
        at = @At(value = "INVOKE", target = "Lat/petrak/hexcasting/api/casting/eval/vm/CastingVM;queueExecuteAndWrapIota")
    )
    private static ExecutionClientView hexwright$keepGraspOpen(CastingVM vm, Iota iota, ServerLevel level,
                                                               Operation<ExecutionClientView> original) {
        ExecutionClientView view = original.call(vm, iota, level);
        if (view.isStackClear() && ConstructsGrasp.graspedHere(vm.getEnv())) {
            return new ExecutionClientView(false, view.getResolutionType(), view.getStackDescs(), view.getRavenmind());
        }
        return view;
    }
}
