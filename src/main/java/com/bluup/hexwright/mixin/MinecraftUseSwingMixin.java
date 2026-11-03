package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftUseSwingMixin {

    @WrapOperation(
        method = "startUseItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"
        )
    )
    private void hexwright$noSwingForUse(LocalPlayer player, InteractionHand hand, Operation<Void> original) {
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().getItem() instanceof AnimatedWeapon) {
            return;
        }
        original.call(player, hand);
    }
}
