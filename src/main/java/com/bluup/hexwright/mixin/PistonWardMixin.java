package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.worldgen.AreaWard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonWardMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private List<BlockPos> toPush;

    @Shadow
    @Final
    private List<BlockPos> toDestroy;

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void hexwright$refuseWardedPush(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) {
            return;
        }
        for (BlockPos pos : this.toPush) {
            if (AreaWard.sealed(this.level, pos)) {
                callback.setReturnValue(false);
                return;
            }
        }
        for (BlockPos pos : this.toDestroy) {
            if (AreaWard.sealed(this.level, pos)) {
                callback.setReturnValue(false);
                return;
            }
        }
    }
}
