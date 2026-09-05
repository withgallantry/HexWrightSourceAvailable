package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommonUniforms.class, remap = false)
public class IrisPortalUniformsMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"))
    private static void hexwright$portalUniforms(UniformHolder uniforms, IdMap ids,
                                                PackDirectives directives, FrameUpdateNotifier notifier,
                                                CallbackInfo ci) {
        uniforms.uniform4f(UniformUpdateFrequency.PER_FRAME, "hexwright_ClipPlane", () ->
            PortalViewRenderer.shaderClipPlane(CapturedRenderingState.INSTANCE.getGbufferModelView(),
                CapturedRenderingState.INSTANCE.getGbufferProjection()));
        uniforms.uniform2f(UniformUpdateFrequency.PER_FRAME, "hexwright_InverseViewport", () -> {
            var target = Minecraft.getInstance().getMainRenderTarget();
            return new Vector2f(1.0f / target.width, 1.0f / target.height);
        });
    }
}
