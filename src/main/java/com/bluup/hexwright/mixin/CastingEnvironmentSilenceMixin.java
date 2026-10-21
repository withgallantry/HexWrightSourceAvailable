package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell;
import com.bluup.hexwright.server.weapon.Silence;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentSilenceMixin {

    @Shadow(remap = false)
    @Nullable
    protected abstract ResourceLocation actionKey(PatternShapeMatch match);

    @Inject(method = "precheckAction", at = @At("HEAD"), remap = false)
    private void hexwright$refuseSilenced(PatternShapeMatch match, CallbackInfo ci) {
        CastingEnvironment self = (CastingEnvironment) (Object) this;
        if (Silence.isSilenced(self.getCastingEntity())) {
            throw new MishapDisallowedSpell("hexwright_silenced", actionKey(match));
        }
    }
}
