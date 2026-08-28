package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.vehicle.VehicleEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class VehicleRiderFallDamageMixin {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void hexwright$noFallDamageOnVehicle(float fallDistance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity.getVehicle() instanceof VehicleEntity) {
            entity.resetFallDistance();
            cir.setReturnValue(false);
        }
    }
}
