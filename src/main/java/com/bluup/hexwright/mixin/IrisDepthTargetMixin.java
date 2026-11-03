package com.bluup.hexwright.mixin;

import com.bluup.hexwright.Hexwright;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderTargets.class, remap = false)
public class IrisDepthTargetMixin {
    @Shadow private int currentDepthTexture;
    @Shadow private int cachedDepthBufferVersion;
    @Unique private boolean hexwright$reported;

    @Inject(method = "resizeIfNeeded", at = @At("HEAD"))
    private void hexwright$reattachDepth(int version, int texture, int width, int height,
                                        DepthBufferFormat format, PackDirectives directives,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (currentDepthTexture != texture) {
            cachedDepthBufferVersion = version ^ -1;
        }
    }

    @Inject(method = "resizeIfNeeded", at = @At("RETURN"))
    private void hexwright$auditDepth(int version, int texture, int width, int height,
                                     DepthBufferFormat format, PackDirectives directives,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (currentDepthTexture != texture && !hexwright$reported) {
            hexwright$reported = true;
            Hexwright.LOGGER.error("[iris-audit] depth identity mismatch after resizeIfNeeded: "
                + "cached={} live={} expected={} live={} cachedVersion={} suppliedVersion={}",
                currentDepthTexture, GL11.glIsTexture(currentDepthTexture), texture,
                GL11.glIsTexture(texture), cachedDepthBufferVersion, version,
                new IllegalStateException("First mismatched depth attachment"));
        }
    }
}
