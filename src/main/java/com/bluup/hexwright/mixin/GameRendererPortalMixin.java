package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererPortalMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void hexwright$renderPortalViews(float partialTick, long nanos, PoseStack poseStack, CallbackInfo ci) {
        PortalViewRenderer.onRenderLevelStart((GameRenderer) (Object) this, partialTick, nanos);
    }
}
