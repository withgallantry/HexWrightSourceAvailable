package com.bluup.hexwright.mixin;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PipelineManager.class, remap = false)
public interface IrisPipelineManagerAccessor {
    @Accessor("pipeline") void hexwright$pipeline(WorldRenderingPipeline pipeline);
}
