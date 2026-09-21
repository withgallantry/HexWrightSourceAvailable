package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.armour.PassagePortalVisualClient;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class PassageFreezeInputMixin {

    @Inject(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void hexwright$freezeForPassage(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!PassagePortalVisualClient.freezesInput(self)) {
            return;
        }
        Input input = self.input;
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
