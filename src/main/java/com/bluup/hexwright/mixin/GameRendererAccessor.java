package com.bluup.hexwright.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("renderHand")
    boolean hexwright$getRenderHand();

    @Accessor("renderHand")
    void hexwright$setRenderHand(boolean renderHand);
}
