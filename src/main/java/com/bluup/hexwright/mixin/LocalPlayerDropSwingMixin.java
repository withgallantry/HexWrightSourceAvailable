package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerDropSwingMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"))
    private void hexwright$noteWeaponInHand(boolean fullStack, CallbackInfoReturnable<Boolean> cir,
                                            @Share("weaponInHand") LocalBooleanRef weaponInHand) {
        weaponInHand.set(((LocalPlayer) (Object) this).getMainHandItem().getItem() instanceof AnimatedWeapon);
    }

    @Inject(method = "drop(Z)Z", at = @At("RETURN"), cancellable = true)
    private void hexwright$noSwingForDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir,
                                          @Share("weaponInHand") LocalBooleanRef weaponInHand) {
        if (weaponInHand.get()) {
            cir.setReturnValue(false);
        }
    }
}
