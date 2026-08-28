package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.armour.DomitorPowers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class DomitorWardMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void hexwright$shrugOffFireAndBlast(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (DomitorPowers.wardAgainst(self, source) >= 1.0f) {
            self.clearFire();
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float hexwright$softenFireAndBlast(float amount, DamageSource source) {
        float ward = DomitorPowers.wardAgainst((LivingEntity) (Object) this, source);
        return ward <= 0.0f ? amount : amount * (1.0f - ward);
    }
}
