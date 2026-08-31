package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.model.BakedModel;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.function.Consumer;

public final class EmissiveBloom {
    private static final int DEBUG_SKIP_COMPOSITE = 1;
    private static final int DEBUG_VIEW_GLOW = 2;
    private static final int DEBUG_CAPTURE_ONLY = 3;

    private static final MultiBufferSource.BufferSource GLOW_BUFFER =
        MultiBufferSource.immediate(new BufferBuilder(2048));


    private static boolean capturing;
    private static boolean anythingCaptured;

    private EmissiveBloom() {
    }

    public static void register() {
        EmissiveBloomConfigManager.reload();
        EmissiveGlowLayer.register();
        EmissiveBloomShaders.register();
        EmissiveBloomCommands.register();

        WorldRenderEvents.START.register(context -> beginWorldPass());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            EmissiveBloomTargets.close();
            EmissiveBloomShaders.close();
        });
    }

    public static boolean capturing() {
        return capturing;
    }

    private static void beginWorldPass() {
        capturing = false;
        anythingCaptured = false;

        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        if (!config.enabled || config.intensity <= 0.0f) {
            return;
        }
        if (config.disableWhenShaderPackActive && IrisCompat.isShaderPackActive()) {
            return;
        }
        if (PortalViewRenderer.isRenderingView()) {
            return;
        }
        if (!EmissiveBloomShaders.shadersReady()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        RenderTarget main = client.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) {
            return;
        }
        if (!EmissiveBloomTargets.ensure(main,
            Math.max(1, Math.round(main.width * config.framebufferScale)),
            Math.max(1, Math.round(main.height * config.framebufferScale)))) {
            return;
        }

        clearGlowBuffer();

        main.bindWrite(true);
        capturing = true;
    }

    static void captureGlow(PoseStack.Pose pose, BakedModel glow, RenderType layer,
                            int overlay, float red, float green, float blue) {
        capture(layer, consumer ->
            EmissiveItemModels.emitGlowQuads(consumer, pose, glow, overlay, red, green, blue));
    }

    public static void capture(RenderType layer, Consumer<VertexConsumer> emitter) {
        if (!capturing || PortalViewRenderer.isRenderingView()) {
            return;
        }

        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        BlendMode previousBlendMode = BlendModeAccessor.hexwright$getLastApplied();
        EmissiveBloomTargets.glow().bindWrite(false);

        emitter.accept(GLOW_BUFFER.getBuffer(layer));
        GLOW_BUFFER.endBatch();

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        BlendModeAccessor.hexwright$setLastApplied(previousBlendMode);
        anythingCaptured = true;
    }

    public static void finishFrame() {
        if (!capturing) {
            return;
        }
        capturing = false;
        if (!anythingCaptured) {
            return;
        }

        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        Minecraft client = Minecraft.getInstance();
        RenderTarget main = client.getMainRenderTarget();

        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        BlendMode previousBlendMode = BlendModeAccessor.hexwright$getLastApplied();

        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        try {
            if (config.debugMode != DEBUG_CAPTURE_ONLY) {
                buildGlowLevels(config);
                if (config.debugMode != DEBUG_SKIP_COMPOSITE) {
                    composite(main, config);
                }
            }
        } finally {
            restoreState(main, previous, previousBlendMode);
        }
    }

    private static void buildGlowLevels(EmissiveBloomConfig config) {
        RenderTarget glow = EmissiveBloomTargets.glow();
        RenderTarget tight = EmissiveBloomTargets.tight();
        RenderTarget wide = EmissiveBloomTargets.wide();
        RenderTarget scratch = EmissiveBloomTargets.scratch();

        downsample(glow, tight);
        downsample(tight, wide);

        blurPass(wide, scratch, config.blurRadius / EmissiveBloomTargets.wideWidth(), 0.0f);
        blurPass(scratch, wide, 0.0f, config.blurRadius / EmissiveBloomTargets.wideHeight());
    }

    private static void downsample(RenderTarget from, RenderTarget to) {
        to.bindWrite(true);

        ShaderInstance shader = EmissiveBloomShaders.downsample();
        shader.setSampler("DiffuseSampler", from.getColorTextureId());
        setUniform(shader, "TexelOffset", 0.5f / from.width, 0.5f / from.height);
        draw(shader);
    }

    private static void blurPass(RenderTarget from, RenderTarget to, float deltaX, float deltaY) {
        to.bindWrite(true);

        ShaderInstance shader = EmissiveBloomShaders.blur();
        shader.setSampler("DiffuseSampler", from.getColorTextureId());
        setUniform(shader, "BlurDelta", deltaX, deltaY);
        draw(shader);
    }

    private static void composite(RenderTarget main, EmissiveBloomConfig config) {
        main.bindWrite(true);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);

        boolean viewGlow = config.debugMode == DEBUG_VIEW_GLOW;
        float strength = viewGlow ? 1.0f : config.intensity;
        compositeLevel(viewGlow, EmissiveBloomTargets.tight(), strength * config.coreStrength);
        compositeLevel(viewGlow, EmissiveBloomTargets.wide(), strength);
    }

    private static void compositeLevel(boolean viewGlow, RenderTarget level, float strength) {
        if (strength <= 0.0f) {
            return;
        }
        ShaderInstance shader = viewGlow ? EmissiveBloomShaders.view() : EmissiveBloomShaders.composite();
        shader.setSampler("DiffuseSampler", level.getColorTextureId());
        setUniform(shader, "Intensity", strength);
        draw(shader);
    }

    private static void clearGlowBuffer() {
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, EmissiveBloomTargets.glow().frameBufferId);
        GlStateManager._clearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
    }

    private static void restoreState(RenderTarget main, int previous, BlendMode previousBlendMode) {
        main.bindWrite(true);
        if (previous != main.frameBufferId) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();

        BlendModeAccessor.hexwright$setLastApplied(previousBlendMode);
    }

    private static void setUniform(ShaderInstance shader, String name, float a) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(a);
        }
    }

    private static void setUniform(ShaderInstance shader, String name, float a, float b) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(a, b);
        }
    }

    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0, -1.0, 0.0).uv(0.0f, 0.0f).endVertex();
        builder.vertex(-1.0, 1.0, 0.0).uv(0.0f, 1.0f).endVertex();
        builder.vertex(1.0, 1.0, 0.0).uv(1.0f, 1.0f).endVertex();
        builder.vertex(1.0, -1.0, 0.0).uv(1.0f, 0.0f).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }
}
