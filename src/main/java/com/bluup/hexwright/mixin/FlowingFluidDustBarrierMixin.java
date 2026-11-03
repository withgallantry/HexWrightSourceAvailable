package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.dust.DustBarrier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
abstract class FlowingFluidDustBarrierMixin {

    @Inject(method = "canSpreadTo", at = @At("RETURN"), cancellable = true)
    private void hexwright$dustDams(BlockGetter getter, BlockPos fromPos, BlockState fromState,
                                    Direction direction, BlockPos toPos, BlockState toState,
                                    FluidState toFluidState, Fluid fluid,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !(getter instanceof Level level) || level.isClientSide) {
            return;
        }
        if (DustBarrier.dams(level, fromPos, toPos)) {
            cir.setReturnValue(false);
            DustBarrier.press(level, fromPos);
        }
    }
}
