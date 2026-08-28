package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.bluup.hexwright.server.worldgen.AreaWard;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentDungeonMixin {

    @Inject(method = "hasEditPermissionsAt", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexwright$sealDungeon(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        CastingEnvironment self = (CastingEnvironment) (Object) this;
        if (self instanceof AreaWard.Unsealed unsealed && unsealed.ignoresAreaWard()) {
            return;
        }
        if (AreaWard.sealed(self.getWorld(), pos)) {
            cir.setReturnValue(false);
        }
    }
}
