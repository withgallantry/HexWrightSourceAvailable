package com.bluup.hexwright.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("renderHand")
    boolean hexwright$getRenderHand();

    @Accessor("renderHand")
    void hexwright$setRenderHand(boolean renderHand);

    @Mutable
    @Accessor("mainCamera")
    void hexwright$setCamera(Camera camera);

    @Mutable
    @Accessor("lightTexture")
    void hexwright$setLightTexture(LightTexture lightTexture);
}
