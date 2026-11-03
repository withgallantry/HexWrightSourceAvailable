package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityBlightMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void hexwright$blighted(float amount, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).hasEffect(HexwrightEffects.BLIGHTED)) {
            ci.cancel();
        }
    }
}
