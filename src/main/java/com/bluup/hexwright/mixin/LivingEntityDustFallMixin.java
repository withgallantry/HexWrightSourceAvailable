package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.dust.DustSupport;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityDustFallMixin {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void hexwright$caughtByDust(float distance, float multiplier, DamageSource source,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (DustSupport.catches((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
