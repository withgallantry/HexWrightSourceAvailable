package com.bluup.hexwright.mixin;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSwingTraceMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"))
    private void hexwright$traceWeaponSwing(InteractionHand hand, CallbackInfo ci) {
        if (!HexwrightDebug.on(HexwrightDebug.WEAPON)) {
            return;
        }
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.getMainHandItem().getItem() instanceof AnimatedWeapon) {
            HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] local swing {} with {} on tick {}",
                hand, self.getMainHandItem().getItem(), self.tickCount, new Throwable("swing caller"));
        }
    }
}
