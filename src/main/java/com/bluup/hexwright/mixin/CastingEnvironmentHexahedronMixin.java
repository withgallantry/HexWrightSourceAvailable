package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.bluup.hexwright.server.hexahedron.HexahedronWard;
import com.bluup.hexwright.server.hexahedron.MishapHexahedron;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentHexahedronMixin {

    @Inject(method = "precheckAction", at = @At("HEAD"), remap = false)
    private void hexwright$hexahedronDisrupts(PatternShapeMatch match, CallbackInfo ci) {
        if (HexahedronWard.disrupts((CastingEnvironment) (Object) this)) {
            throw new MishapHexahedron();
        }
    }
}
