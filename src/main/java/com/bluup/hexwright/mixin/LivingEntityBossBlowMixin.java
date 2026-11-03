package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.boss.BossDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityBossBlowMixin {

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean hexwright$noteShieldedBossBlow(LivingEntity self, DamageSource source, Operation<Boolean> original,
                                                   @Local(argsOnly = true) float amount,
                                                   @Share("throughShield") LocalFloatRef through) {
        boolean blocked = original.call(self, source);
        if (blocked) {
            through.set(BossDamage.throughShield(self, source, amount));
        }
        return blocked;
    }

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void hexwright$letThroughShield(LivingEntity self, DamageSource source, float amount, Operation<Void> original,
                                            @Share("throughShield") LocalFloatRef through) {
        original.call(self, source, Math.max(amount, through.get()));
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void hexwright$bossBlowLanded(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            BossDamage.landed((LivingEntity) (Object) this, source);
        }
    }
}
