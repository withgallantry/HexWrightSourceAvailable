package com.bluup.hexwright.mixin;

import com.lowdragmc.photon.client.postprocessing.BloomEffect;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = BloomEffect.class, remap = false)
public interface BloomEffectAccessor {

    @Accessor("INPUT")
    static RenderTarget hexwright$getInput() {
        throw new AssertionError("mixin accessor not applied");
    }

    @Accessor("TRANSLUCENT_INPUT")
    static RenderTarget hexwright$getTranslucentInput() {
        throw new AssertionError("mixin accessor not applied");
    }

    @Invoker("hookColorBuffer")
    static void hexwright$hookColorBuffer(RenderTarget target, int textureId, int attachment) {
        throw new AssertionError("mixin invoker not applied");
    }

    @Invoker("hookDepthBuffer")
    static void hexwright$hookDepthBuffer(RenderTarget target, int textureId) {
        throw new AssertionError("mixin invoker not applied");
    }
}
