package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.mixin.GameRendererAccessor;
import com.bluup.hexwright.mixin.MinecraftPortalAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;

final class PortalRenderState implements AutoCloseable {
    private final Minecraft mc = Minecraft.getInstance();
    private final ClientLevel level = mc.level;
    private final LevelRenderer renderer = mc.levelRenderer;
    private final RenderTarget target = mc.getMainRenderTarget();
    private final Camera camera = mc.gameRenderer.getMainCamera();
    private final LightTexture lightmap = mc.gameRenderer.lightTexture();
    private final boolean renderHand = ((GameRendererAccessor) mc.gameRenderer).hexwright$getRenderHand();
    private final boolean smartCull = mc.smartCull;
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final com.mojang.blaze3d.vertex.VertexSorting sorting = RenderSystem.getVertexSorting();
    private final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack().last().pose());
    private final ShaderInstance shader = RenderSystem.getShader();
    private final BlendMode blendMode = BlendModeAccessor.hexwright$getLastApplied();
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int[] viewport = new int[4];
    private final int[] scissor = new int[4];
    private final boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final ByteBuffer colorMask = ByteBuffer.allocateDirect(4);
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final float[] fog = RenderSystem.getShaderFogColor().clone();
    private final float fogStart = RenderSystem.getShaderFogStart();
    private final float fogEnd = RenderSystem.getShaderFogEnd();
    private final com.mojang.blaze3d.shaders.FogShape fogShape = RenderSystem.getShaderFogShape();
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] shaderTextures = new int[12];
    private final int[] textures = new int[Math.max(12, GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS))];
    private final int[] samplers = new int[textures.length];
    private final Runnable restoreIris = IrisCompat.capturePortalState();

    PortalRenderState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        for (int unit = 0; unit < shaderTextures.length; unit++) {
            shaderTextures[unit] = RenderSystem.getShaderTexture(unit);
        }
        for (int unit = 0; unit < textures.length; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            samplers[unit] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit);
        }
        GL13.glActiveTexture(activeTexture);
    }

    @Override
    public void close() {
        var access = (GameRendererAccessor) mc.gameRenderer;
        mc.level = level;
        ((MinecraftPortalAccessor) mc).hexwright$setLevelRenderer(renderer);
        ((MinecraftPortalAccessor) mc).hexwright$setMainRenderTarget(target);
        access.hexwright$setCamera(camera);
        access.hexwright$setLightTexture(lightmap);
        access.hexwright$setRenderHand(renderHand);
        mc.smartCull = smartCull;
        mc.getEntityRenderDispatcher().prepare(level, camera, mc.crosshairPickEntity);
        mc.getBlockEntityRenderDispatcher().prepare(level, camera, mc.hitResult);
        try {
            restoreIris.run();
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.getModelViewStack().last().pose().set(modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShaderFogColor(fog[0], fog[1], fog[2], fog[3]);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            for (int unit = 0; unit < shaderTextures.length; unit++) {
                RenderSystem.setShaderTexture(unit, shaderTextures[unit]);
            }
            for (int unit = 0; unit < textures.length; unit++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[unit]);
                if (unit < shaderTextures.length) GlStateManager._bindTexture(textures[unit]);
                GL33.glBindSampler(unit, samplers[unit]);
            }
            RenderSystem.activeTexture(activeTexture);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                colorMask.get(2) != 0, colorMask.get(3) != 0);
            BlendModeAccessor.hexwright$setLastApplied(blendMode);
            if (scissorEnabled) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            else RenderSystem.disableScissor();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GlStateManager._glUseProgram(program);
        }
    }
}
