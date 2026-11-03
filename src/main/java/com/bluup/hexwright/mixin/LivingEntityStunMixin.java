package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.effect.HexwrightEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingEntityStunMixin {

    @ModifyReturnValue(method = "isImmobile", at = @At("RETURN"))
    private boolean hexwright$heldByStun(boolean immobile) {
        LivingEntity self = (LivingEntity) (Object) this;
        return immobile || self.hasEffect(HexwrightEffects.STUNNED)
            || (!(self instanceof Player) && self.hasEffect(HexwrightEffects.SOUL_CLEAVED));
    }
}
