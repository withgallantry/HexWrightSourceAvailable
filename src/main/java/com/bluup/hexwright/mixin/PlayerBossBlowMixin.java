package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.boss.BossDamage;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerBossBlowMixin {

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void hexwright$noteBlow(DamageSource source, float amount, CallbackInfo ci,
                                    @Share("raw") LocalFloatRef raw) {
        raw.set(amount);
    }

    @ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float hexwright$bossTerms(float mitigated, @Local(argsOnly = true) DamageSource source,
                                      @Share("raw") LocalFloatRef raw) {
        return BossDamage.landing((Player) (Object) this, source, raw.get(), mitigated);
    }
}
