package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.AbyssCleaverItem;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerAbyssCleaverMixin {

    @ModifyArg(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
        index = 1)
    private float hexwright$measureAgainstPool(float damage, @Local(argsOnly = true) Entity target) {
        return AbyssCleaverItem.blowDamage((Player) (Object) this, target, damage);
    }

    @ModifyVariable(method = "setAbsorptionAmount", at = @At("HEAD"), argsOnly = true)
    private float hexwright$unshielded(float amount) {
        return AbyssCleaverItem.absorptionFor((Player) (Object) this, amount);
    }
}
