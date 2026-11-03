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

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class EmissiveBloom {
    private static final int DEBUG_SKIP_COMPOSITE = 1;
    private static final int DEBUG_VIEW_GLOW = 2;
    private static final int DEBUG_CAPTURE_ONLY = 3;

    private static final MultiBufferSource.BufferSource GLOW_BUFFER =
        MultiBufferSource.immediate(new BufferBuilder(2048));


    private static boolean capturing;
    private static boolean anythingCaptured;
    private static boolean worldPassDone;

    private static boolean deferToIrisFinalPass;

    private static final List<PendingGlow> DEFERRED = new ArrayList<>();

    private static final PoseStack REPLAY = new PoseStack();

    private static final int[] VIEWPORT = new int[4];

    private EmissiveBloom() {
    }

    public static void register() {
        EmissiveBloomConfigManager.reload();
        EmissiveGlowLayer.register();
        EmissiveBloomShaders.register();
        EmissiveBloomCommands.register();

        WorldRenderEvents.START.register(context -> beginWorldPass());
        WorldRenderEvents.LAST.register(context -> endWorldPass());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            EmissiveBloomTargets.close();
            EmissiveBloomShaders.close();
        });
    }

    public static boolean capturing() {
        return capturing;
    }

    static String haloDisposition(boolean handPose) {
        if (!capturing) {
            EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
            if (!config.enabled || config.intensity <= 0.0f) {
                return "not captured - halo disabled in the config";
            }
            if (IrisCompat.isShaderPackActive() && config.disableWhenShaderPackActive) {
                return "not captured - halo switched off under shader packs";
            }
            if (!EmissiveBloomShaders.shadersReady()) {
                return "not captured - the halo's shaders are not loaded";
            }
            return "not captured - the pass never armed this frame";
        }
        if (PortalViewRenderer.isRenderingView()) {
            return "not captured - inside a portal view";
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return "not captured - Iris shadow pass";
        }
        return !worldPassDone && !handPose
            ? "deferred to the end of the world pass"
            : "captured immediately";
    }

    private static void beginWorldPass() {
        EmissiveItemTrace.nextFrame();

        capturing = false;
        anythingCaptured = false;
        worldPassDone = false;
        deferToIrisFinalPass = false;
        DEFERRED.clear();

        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        if (!config.enabled || config.intensity <= 0.0f) {
            return;
        }
        boolean shaderPack = IrisCompat.isShaderPackActive();
        if (shaderPack && config.disableWhenShaderPackActive) {
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
        deferToIrisFinalPass = shaderPack;

        main.bindWrite(true);
        capturing = true;
    }

    static void captureGlow(PoseStack.Pose pose, BakedModel glow, RenderType layer,
                            int overlay, float red, float green, float blue, boolean handPose) {
        if (!capturing || PortalViewRenderer.isRenderingView()
            || IrisCompat.isRenderingShadowPass()) {
            return;
        }
        if (!worldPassDone && !handPose) {
            DEFERRED.add(new PendingGlow(new Matrix4f(pose.pose()), new Matrix3f(pose.normal()),
                glow, layer, overlay, red, green, blue));
            return;
        }
        capture(layer, consumer ->
            EmissiveItemModels.emitGlowQuads(consumer, pose, glow, overlay, red, green, blue));
    }

    private static void endWorldPass() {
        if (!capturing || PortalViewRenderer.isRenderingView()) {
            return;
        }
        replayDeferred();
        if (!deferToIrisFinalPass) {
            compositeWorldGlow();
        }
        worldPassDone = true;
    }

    private static void replayDeferred() {
        for (PendingGlow pending : DEFERRED) {
            REPLAY.setIdentity();
            PoseStack.Pose pose = REPLAY.last();
            pose.pose().set(pending.pose());
            pose.normal().set(pending.normal());
            capture(pending.layer(), consumer -> EmissiveItemModels.emitGlowQuads(
                consumer, pose, pending.glow(), pending.overlay(),
                pending.red(), pending.green(), pending.blue()));
        }
        DEFERRED.clear();
    }

    private record PendingGlow(Matrix4f pose, Matrix3f normal, BakedModel glow, RenderType layer,
                               int overlay, float red, float green, float blue) {
    }

    public static void capture(RenderType layer, Consumer<VertexConsumer> emitter) {
        if (!capturing || PortalViewRenderer.isRenderingView()
            || IrisCompat.isRenderingShadowPass()) {
            return;
        }

        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        BlendMode previousBlendMode = BlendModeAccessor.hexwright$getLastApplied();

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);
        RenderTarget glow = EmissiveBloomTargets.glow();

        IrisCompat.beginPrivatePass();
        try {
            glow.bindWrite(true);

            emitter.accept(GLOW_BUFFER.getBuffer(layer));
            GLOW_BUFFER.endBatch();
        } finally {
            IrisCompat.endPrivatePass();
        }

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
        BlendModeAccessor.hexwright$setLastApplied(previousBlendMode);
        anythingCaptured = true;
    }

    public static void compositeWorldGlow() {
        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        if (!capturing || !anythingCaptured || config.debugMode != 0) {
            return;
        }
        runBlurAndComposite(config);
        clearGlowBuffer();
        anythingCaptured = false;
    }

    public static void finishFrame() {
        if (!capturing) {
            return;
        }
        if (deferToIrisFinalPass) {
            capturing = false;
            anythingCaptured = false;
            return;
        }
        capturing = false;
        if (!anythingCaptured) {
            return;
        }
        runBlurAndComposite(EmissiveBloomConfigManager.get());
    }

    public static void onIrisFinalPassComplete() {
        if (!capturing || !deferToIrisFinalPass || PortalViewRenderer.isRenderingView()) {
            return;
        }
        if (!anythingCaptured) {
            return;
        }
        runBlurAndComposite(EmissiveBloomConfigManager.get());
        anythingCaptured = false;
    }

    private static void runBlurAndComposite(EmissiveBloomConfig config) {
        Minecraft client = Minecraft.getInstance();
        RenderTarget main = client.getMainRenderTarget();

        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        BlendMode previousBlendMode = BlendModeAccessor.hexwright$getLastApplied();

        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        IrisCompat.beginPrivatePass();
        try {
            if (config.debugMode != DEBUG_CAPTURE_ONLY) {
                buildGlowLevels(config);
                if (config.debugMode != DEBUG_SKIP_COMPOSITE) {
                    composite(main, config);
                }
            }
        } finally {
            IrisCompat.endPrivatePass();
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

        if (config.debugMode == DEBUG_VIEW_GLOW) {
            viewGlow(config);
            return;
        }

        additiveBlend();
        compositeLevel(false, EmissiveBloomTargets.tight(), config.intensity * config.coreStrength);
        compositeLevel(false, EmissiveBloomTargets.wide(), config.intensity);
    }

    private static void viewGlow(EmissiveBloomConfig config) {
        RenderSystem.disableBlend();
        BlendModeAccessor.hexwright$setLastApplied(null);
        compositeLevel(true, EmissiveBloomTargets.tight(), config.coreStrength);

        additiveBlend();
        compositeLevel(true, EmissiveBloomTargets.wide(), 1.0f);
    }

    private static void additiveBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }

    private static void compositeLevel(boolean viewGlow, RenderTarget level, float strength) {
        if (strength <= 0.0f && !viewGlow) {
            return;
        }
        ShaderInstance shader = viewGlow ? EmissiveBloomShaders.view() : EmissiveBloomShaders.composite();
        shader.setSampler("DiffuseSampler", level.getColorTextureId());
        setUniform(shader, "Intensity", Math.max(strength, 0.0f));
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
