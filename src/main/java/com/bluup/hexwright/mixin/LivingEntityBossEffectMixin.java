package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.boss.Bosses;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityBossEffectMixin {

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void hexwright$bossShrugsOff(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (Bosses.shrugsOff((LivingEntity) (Object) this, effect)) {
            cir.setReturnValue(false);
        }
    }
}
