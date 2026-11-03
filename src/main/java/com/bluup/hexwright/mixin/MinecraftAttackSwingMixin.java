package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.weapon.WeaponAttackPacing;
import com.bluup.hexwright.client.weapon.WeaponClickTarget;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftAttackSwingMixin {

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    protected abstract boolean startAttack();

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void hexwright$oneBlowPerSwing(CallbackInfoReturnable<Boolean> cir,
                                           @Share("dig") LocalBooleanRef dig) {
        Minecraft mc = (Minecraft) (Object) this;
        dig.set(WeaponClickTarget.isDig(mc));
        if (!WeaponAttackPacing.mayAttack(mc, dig.get())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void hexwright$releaseKeptClick(CallbackInfo ci) {
        if (WeaponAttackPacing.takeBufferedClick((Minecraft) (Object) this)) {
            this.startAttack();
        }
    }

    @ModifyExpressionValue(
        method = "startAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;")
    )
    private HitResult.Type hexwright$swingPastBlock(HitResult.Type type, @Share("dig") LocalBooleanRef dig) {
        return type == HitResult.Type.BLOCK && !dig.get() ? HitResult.Type.MISS : type;
    }

    @ModifyExpressionValue(
        method = "continueAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;")
    )
    private HitResult.Type hexwright$holdPastBlock(HitResult.Type type) {
        return type == HitResult.Type.BLOCK && !WeaponClickTarget.isDig((Minecraft) (Object) this)
            ? HitResult.Type.MISS
            : type;
    }

    @WrapOperation(
        method = "startAttack",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"
        )
    )
    private void hexwright$attackSwing(LocalPlayer player, InteractionHand hand, Operation<Void> original,
                                       @Share("dig") LocalBooleanRef dig) {
        if (dig.get()) {
            original.call(player, hand);
            return;
        }
        HexwrightNetworking.sendAttackSwing(this.hitResult != null && this.hitResult.getType() == HitResult.Type.ENTITY);
        if (!AnimatedWeapon.ownsAttackSwing(player)) {
            original.call(player, hand);
        }
    }
}
