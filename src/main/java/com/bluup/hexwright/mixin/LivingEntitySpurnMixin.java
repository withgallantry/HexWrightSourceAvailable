package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.Silence;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingEntitySpurnMixin {

    @ModifyReturnValue(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"))
    private boolean hexwright$spurnsWielder(boolean canAttack, LivingEntity target) {
        return canAttack && !Silence.spurns((LivingEntity) (Object) this, target);
    }
}
