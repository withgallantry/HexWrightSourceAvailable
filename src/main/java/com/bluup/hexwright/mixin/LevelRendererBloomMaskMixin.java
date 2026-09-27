package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.weapon.SlashBloom;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererBloomMaskMixin {

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=particles"))
    private void hexwright$flushBloomMasks(CallbackInfo ci) {
        SlashBloom.flushPending();
    }
}
