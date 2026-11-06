package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.effect.HexwrightEffects;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDivineStrainMixin {

    @Shadow
    @Final
    private Map<MobEffect, MobEffectInstance> activeEffects;

    @Inject(method = "removeAllEffects", at = @At("HEAD"))
    private void hexwright$holdDivineStrain(CallbackInfoReturnable<Boolean> cir,
                                            @Share("divineStrain") LocalRef<MobEffectInstance> held) {
        held.set(this.activeEffects.remove(HexwrightEffects.DIVINE_STRAIN));
    }

    @Inject(method = "removeAllEffects", at = @At("RETURN"))
    private void hexwright$restoreDivineStrain(CallbackInfoReturnable<Boolean> cir,
                                               @Share("divineStrain") LocalRef<MobEffectInstance> held) {
        if (held.get() != null) {
            this.activeEffects.put(HexwrightEffects.DIVINE_STRAIN, held.get());
        }
    }
}
