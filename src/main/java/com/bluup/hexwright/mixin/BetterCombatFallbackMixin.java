package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.bettercombat.logic.WeaponAttributesFallback", remap = false)
public abstract class BetterCombatFallbackMixin {

    @WrapOperation(
        method = "initialize",
        at = @At(
            value = "INVOKE",
            target = "Lnet/bettercombat/utils/PatternMatching;matches(Ljava/lang/String;Ljava/lang/String;)Z",
            ordinal = 0
        ),
        remap = false,
        require = 0
    )
    private static boolean hexwright$blacklistAnimatedWeapons(String itemId, String regex, Operation<Boolean> original) {
        if (original.call(itemId, regex)) {
            return true;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        return id != null && BuiltInRegistries.ITEM.get(id) instanceof AnimatedWeapon;
    }
}
