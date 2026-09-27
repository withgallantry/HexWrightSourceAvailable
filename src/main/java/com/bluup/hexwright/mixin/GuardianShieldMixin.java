package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.powerorb.GuardianConstructEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class GuardianShieldMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void hexwright$guardianShield(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (GuardianConstructEntity.shields((LivingEntity) (Object) this, source, amount)) {
            cir.setReturnValue(false);
        }
    }
}
