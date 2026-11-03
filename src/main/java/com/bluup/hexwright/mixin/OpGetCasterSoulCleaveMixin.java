package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.GarbageIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.casting.actions.selectors.OpGetCaster;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(OpGetCaster.class)
public abstract class OpGetCasterSoulCleaveMixin {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexwright$cleavedSoulIsGarbage(List<? extends Iota> args, CastingEnvironment env,
                                                CallbackInfoReturnable<List<Iota>> cir) {
        LivingEntity caster = env.getCastingEntity();
        if (caster != null && caster.hasEffect(HexwrightEffects.SOUL_CLEAVED)) {
            cir.setReturnValue(List.of(new GarbageIota()));
        }
    }
}
