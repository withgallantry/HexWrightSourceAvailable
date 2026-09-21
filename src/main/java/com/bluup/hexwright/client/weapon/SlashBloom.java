package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.server.weapon.SlashStyle;
import com.lowdragmc.photon.client.gameobject.emitter.PhotonParticleRenderType;
import com.lowdragmc.photon.client.postprocessing.BloomEffect;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.function.Consumer;

public final class SlashBloom {

    private static final MultiBufferSource.BufferSource BLOOM_BUFFER =
        MultiBufferSource.immediate(new BufferBuilder(2048));

    private static final int[] MASK_ONLY = {GL30.GL_COLOR_ATTACHMENT1};

    private static final int[] SCENE_AND_MASK = {GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1};

    private static final int[] VIEWPORT = new int[4];

    private static final float MAX_MULTIPLIER = 4.0f;

    private static float crescentMultiplier = 1.0f;
    private static float waveMultiplier = 1.0f;

    private SlashBloom() {
    }

    public static float crescentStrength(SlashStyle style) {
        return style.crescentBloom() * crescentMultiplier;
    }

    public static float waveStrength(SlashStyle style) {
        return style.waveBloom() * waveMultiplier;
    }

    public static float crescentMultiplier() {
        return crescentMultiplier;
    }

    public static float waveMultiplier() {
        return waveMultiplier;
    }

    public static void setCrescentMultiplier(float value) {
        crescentMultiplier = Mth.clamp(value, 0.0f, MAX_MULTIPLIER);
    }

    public static void setWaveMultiplier(float value) {
        waveMultiplier = Mth.clamp(value, 0.0f, MAX_MULTIPLIER);
    }


    public static void capture(RenderType layer, Consumer<VertexConsumer> emitter) {
        if (IrisCompat.isRenderingShadowPass() || PortalViewRenderer.isRenderingView()) {
            return;
        }

        RenderTarget input = BloomEffect.getInput();
        if (input == null) {
            return;
        }

        int previousFrameBuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        BlendMode previousBlend = BlendModeAccessor.hexwright$getLastApplied();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        IrisCompat.beginPrivatePass();
        try {
            input.bindWrite(true);
            GL20.glDrawBuffers(MASK_ONLY);
            emitter.accept(BLOOM_BUFFER.getBuffer(layer));
        } finally {
            BLOOM_BUFFER.endBatch();
            GL20.glDrawBuffers(SCENE_AND_MASK);
            IrisCompat.endPrivatePass();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFrameBuffer);
            GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
            BlendModeAccessor.hexwright$setLastApplied(previousBlend);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }

        PhotonParticleRenderType.bloomMark = true;
    }

    public static VertexConsumer dimmed(VertexConsumer inner, float strength) {
        return strength >= 1.0f ? inner : new Dimmed(inner, strength);
    }

    private record Dimmed(VertexConsumer inner, float strength) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.inner.color(
                (int) (red * this.strength),
                (int) (green * this.strength),
                (int) (blue * this.strength),
                alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            this.inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            this.inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            this.inner.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            this.inner.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            this.inner.unsetDefaultColor();
        }
    }
}
