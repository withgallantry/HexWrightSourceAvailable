package com.bluup.hexwright.mixin;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlendMode.class)
public interface BlendModeAccessor {

    @Accessor("lastApplied")
    static BlendMode hexwright$getLastApplied() {
        throw new AssertionError("mixin accessor not applied");
    }

    @Accessor("lastApplied")
    static void hexwright$setLastApplied(BlendMode mode) {
        throw new AssertionError("mixin accessor not applied");
    }
}
