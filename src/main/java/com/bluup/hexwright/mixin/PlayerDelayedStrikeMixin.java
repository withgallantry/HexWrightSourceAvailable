package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.DelayedStrike;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDelayedStrikeMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void hexwright$holdBlowToImpactFrame(Entity target, CallbackInfo ci) {
        if (DelayedStrike.hold((Player) (Object) this, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void hexwright$strengthOfTheClick(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (DelayedStrike.isLanding(self)) {
            cir.setReturnValue(DelayedStrike.landingStrength());
        }
    }

    @Inject(method = "resetAttackStrengthTicker", at = @At("HEAD"), cancellable = true)
    private void hexwright$tickerAlreadyReset(CallbackInfo ci) {
        if (DelayedStrike.isLanding((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
