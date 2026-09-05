package com.bluup.hexwright.client.render;

import com.bluup.hexwright.mixin.IrisPipelineAccessor;
import com.bluup.hexwright.mixin.IrisPipelineManagerAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.texture.TextureInfoCache;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public final class IrisPortalState implements Runnable {
    private final PipelineManager manager = Iris.getPipelineManager();
    private final int generation = manager.getVersionCounterForSodiumShaderReload();
    private final WorldRenderingPipeline outer = manager.getPipelineNullable();
    private final boolean beforeTranslucent = outer instanceof IrisPipelineAccessor access
        && access.hexwright$beforeTranslucent();
    private final boolean renderingWorld = outer instanceof IrisPipelineAccessor access
        && access.hexwright$renderingWorld();
    private final WorldRenderingPhase phase = outer == null ? null : outer.getPhase();
    private final Captured captured = Captured.read();
    private final Matrix4f shadowModelView = ShadowRenderer.MODELVIEW;
    private final Matrix4f shadowProjection = ShadowRenderer.PROJECTION;

    public IrisPortalState() {
        if (renderingWorld || ShadowRenderer.ACTIVE) {
            throw new IllegalStateException("Portal views must run before Iris begins the outer world pass");
        }
    }

    public static void prepareWorld() {
        Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
    }

    @Override
    public void run() {
        if (manager != Iris.getPipelineManager()
            || generation != manager.getVersionCounterForSodiumShaderReload()) {
            throw new IllegalStateException("Iris pipelines reloaded during a portal render");
        }
        try {
            detachPortalDepth(manager.getPipelineNullable(), Minecraft.getInstance().getMainRenderTarget());
        } finally {
            ((IrisPipelineManagerAccessor) manager).hexwright$pipeline(outer);
            if (outer instanceof IrisPipelineAccessor access) {
                access.hexwright$renderingWorld(renderingWorld);
                access.hexwright$beforeTranslucent(beforeTranslucent);
                outer.setPhase(phase);
            }
            captured.restore();
            ShadowRenderer.ACTIVE = false;
            ShadowRenderer.MODELVIEW = shadowModelView;
            ShadowRenderer.PROJECTION = shadowProjection;
            ProgramUniforms.clearActiveUniforms();
            ProgramSamplers.clearActiveSamplers();
        }
    }

    private static void detachPortalDepth(WorldRenderingPipeline pipeline, RenderTarget main) {
        if (!(pipeline instanceof IrisPipelineAccessor access) || access.hexwright$destroyed()) {
            return;
        }
        int depth = main.getDepthTextureId();
        DepthBufferFormat format = DepthBufferFormat.fromGlEnumOrDefault(
            TextureInfoCache.INSTANCE.getInfo(depth).getInternalFormat());
        access.hexwright$targets().resizeIfNeeded(((Blaze3dRenderTargetExt) main).iris$getDepthBufferVersion(),
            depth, main.width, main.height, format, access.hexwright$directives());
    }

    private record Captured(Matrix4f modelView, Matrix4f projection, Vector3d fog,
                            float density, float darkness, float tickDelta, float realTickDelta,
                            int blockEntity, int entity, int item, float alpha, float cloudTime) {
        static Captured read() {
            var state = CapturedRenderingState.INSTANCE;
            return new Captured(copy(state.getGbufferModelView()), copy(state.getGbufferProjection()),
                new Vector3d(state.getFogColor()), state.getFogDensity(), state.getDarknessLightFactor(),
                state.getTickDelta(), state.getRealTickDelta(), state.getCurrentRenderedBlockEntity(),
                state.getCurrentRenderedEntity(), state.getCurrentRenderedItem(), state.getCurrentAlphaTest(),
                state.getCloudTime());
        }

        private static Matrix4f copy(Matrix4f matrix) {
            return matrix == null ? null : new Matrix4f(matrix);
        }

        void restore() {
            var state = CapturedRenderingState.INSTANCE;
            if (modelView != null) state.setGbufferModelView(modelView);
            if (projection != null) state.setGbufferProjection(projection);
            state.setFogColor((float) fog.x, (float) fog.y, (float) fog.z);
            state.setFogDensity(density);
            state.setDarknessLightFactor(darkness);
            state.setTickDelta(tickDelta);
            state.setRealTickDelta(realTickDelta);
            state.setCurrentBlockEntity(blockEntity);
            state.setCurrentEntity(entity);
            state.setCurrentRenderedItem(item);
            state.setCurrentAlphaTest(alpha);
            state.setCloudTime(cloudTime);
        }
    }
}
