package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.boss.Bosses;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityBossMindMixin {

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void hexwright$noMindToRend(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (Bosses.hasNoMind((Entity) (Object) this, source)) {
            cir.setReturnValue(true);
        }
    }
}
