package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.render.emissive.EmissiveBloom;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererEmissiveBloomMixin {

    @Inject(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void hexwright$compositeEmissiveBloom(float partialTicks, long finishTimeNano,
                                                  PoseStack poseStack, CallbackInfo ci) {
        EmissiveBloom.finishFrame();
    }
}
