package com.bluup.hexwright.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("needsFullRenderChunkUpdate")
    boolean hexwright$needsFullRenderChunkUpdate();

    @Accessor("lastFullRenderChunkUpdate")
    Future<?> hexwright$lastFullRenderChunkUpdate();

    @Accessor("nextFullUpdateMillis")
    AtomicLong hexwright$nextFullUpdateMillis();

    @Accessor("viewArea")
    net.minecraft.client.renderer.ViewArea hexwright$viewArea();
}
