package com.bluup.hexwright.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftPortalAccessor {

    @Accessor("mainRenderTarget")
    @Mutable
    void hexwright$setMainRenderTarget(RenderTarget target);

    @Accessor("levelRenderer")
    @Mutable
    void hexwright$setLevelRenderer(net.minecraft.client.renderer.LevelRenderer renderer);

    @Accessor("rightClickDelay")
    void hexwright$setRightClickDelay(int delay);
}
