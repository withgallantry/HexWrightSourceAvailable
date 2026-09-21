package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftAttackSwingMixin {

    @Shadow
    @Nullable
    public HitResult hitResult;

    @WrapOperation(
        method = "startAttack",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"
        )
    )
    private void hexwright$noVanillaAttackSwing(LocalPlayer player, InteractionHand hand,
                                                Operation<Void> original) {
        boolean dig = this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK;
        if (!dig && hand == InteractionHand.MAIN_HAND && AnimatedWeapon.ownsAttackSwing(player)) {
            player.connection.send(new ServerboundSwingPacket(hand));
        } else {
            original.call(player, hand);
        }
    }
}
