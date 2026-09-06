package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import com.bluup.hexwright.server.combat.SpellCaster;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OperatorSideEffect.AttemptSpell.class)
public abstract class AttemptSpellCasterMixin {

    @Inject(method = "performEffect", at = @At("HEAD"), remap = false)
    private void hexwright$enterCast(CastingVM vm, CallbackInfo ci) {
        SpellCaster.enter(vm.getEnv().getCastingEntity(), vm.getEnv().getWorld().getGameTime());
    }

    @Inject(method = "performEffect", at = @At("RETURN"), remap = false)
    private void hexwright$exitCast(CastingVM vm, CallbackInfo ci) {
        SpellCaster.exit();
    }
}
