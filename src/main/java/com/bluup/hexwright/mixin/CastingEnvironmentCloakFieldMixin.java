package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.bluup.hexwright.server.cloak.CloakFieldExclusion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentCloakFieldMixin {

    @Inject(method = "isVecInRange", at = @At("RETURN"), cancellable = true, remap = false)
    private void hexwright$excludeFromCloakFields(Vec3 vec, CallbackInfoReturnable<Boolean> cir) {
        CastingEnvironment self = (CastingEnvironment) (Object) this;
        if (cir.getReturnValueZ() && CloakFieldExclusion.excludes(self.getWorld(), vec, self.getCastingEntity())) {
            cir.setReturnValue(false);
        }
    }
}
