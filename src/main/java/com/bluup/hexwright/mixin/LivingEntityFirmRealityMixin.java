package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.dungeon.FirmReality;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingEntityFirmRealityMixin {

    @WrapOperation(
        method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageProtection(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I"))
    private int hexwright$firmReality(Iterable<ItemStack> armour, DamageSource source, Operation<Integer> original) {
        return FirmReality.holds((LivingEntity) (Object) this)
            ? FirmReality.damageProtection(armour, source)
            : original.call(armour, source);
    }
}
