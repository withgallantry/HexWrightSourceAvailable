package com.bluup.hexwright.mixin;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FinalPassRenderer.class, remap = false)
public class IrisFinalTargetMixin {
    @Shadow private int lastColorTextureId;
    @Shadow private int lastColorTextureVersion;
    @Unique private RenderTarget hexwright$lastTarget;
    @Unique private boolean hexwright$reportedReuse;

    @Inject(method = "renderFinalPass", at = @At("HEAD"))
    private void hexwright$reattachChangedTarget(CallbackInfo ci) {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (target != hexwright$lastTarget) {
            int version = ((Blaze3dRenderTargetExt) target).iris$getColorBufferVersion();
            if (hexwright$lastTarget != null && !hexwright$reportedReuse
                && lastColorTextureId == target.getColorTextureId() && lastColorTextureVersion == version) {
                hexwright$reportedReuse = true;
                Hexwright.LOGGER.info("[iris-audit] reattaching reused colour texture {} for a new render target",
                    lastColorTextureId);
            }
            lastColorTextureVersion = version ^ -1;
            hexwright$lastTarget = target;
        }
    }
}
