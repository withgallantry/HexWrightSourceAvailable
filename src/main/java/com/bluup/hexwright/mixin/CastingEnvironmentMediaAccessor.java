package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CastingEnvironment.class)
public interface CastingEnvironmentMediaAccessor {

    @Invoker("extractMediaEnvironment")
    long hexwright$extractMediaEnvironment(long cost, boolean simulate);
}
