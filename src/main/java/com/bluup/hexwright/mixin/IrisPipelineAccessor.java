package com.bluup.hexwright.mixin;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface IrisPipelineAccessor {
    @Accessor("renderTargets") RenderTargets hexwright$targets();
    @Accessor("packDirectives") PackDirectives hexwright$directives();
    @Accessor("isRenderingWorld") boolean hexwright$renderingWorld();
    @Accessor("isRenderingWorld") void hexwright$renderingWorld(boolean value);
    @Accessor("isBeforeTranslucent") boolean hexwright$beforeTranslucent();
    @Accessor("isBeforeTranslucent") void hexwright$beforeTranslucent(boolean value);
    @Accessor("destroyed") boolean hexwright$destroyed();
}
