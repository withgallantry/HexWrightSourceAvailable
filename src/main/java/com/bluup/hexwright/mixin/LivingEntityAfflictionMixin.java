package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.PestiferousItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
abstract class LivingEntityAfflictionMixin {

    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance hexwright$afflicted(MobEffectInstance instance) {
        return PestiferousItem.afflictionFor((LivingEntity) (Object) this, instance);
    }
}
