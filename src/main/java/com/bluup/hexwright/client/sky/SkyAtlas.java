package com.bluup.hexwright.client.sky;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class SkyAtlas {

    static final int FACE_SIZE = 256;
    private static final int TILE = FACE_SIZE + 2;

    private static @Nullable TextureTarget atlas;
    private static @Nullable DynamicTexture noise;
    private static @Nullable SkyLibrary.Sky baked;
    private static int nextFace;

    private SkyAtlas() {
    }

    static RenderTarget update(ShaderInstance backdropShader, SkyLibrary.Sky sky, float time) {
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        PortalViewRenderer.suspendScissor();
        try {
            if (atlas == null) {
                atlas = new TextureTarget(3 * TILE, 2 * TILE, false, Minecraft.ON_OSX);
                atlas.setFilterMode(GL11.GL_LINEAR);
            }
            if (noise == null) {
                noise = new DynamicTexture(noiseTable());
                noise.setFilter(true, false);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            }
            atlas.bindWrite(false);
            if (sky != baked) {
                baked = sky;
                RenderSystem.viewport(0, 0, 3 * TILE, 2 * TILE);
            } else {
                int face = nextFace;
                nextFace = (face + 1) % 6;
                RenderSystem.viewport(face % 3 * TILE, face / 3 * TILE, TILE, TILE);
            }
            backdropShader.setSampler("NoiseSampler", noise);
            backdropShader.safeGetUniform("FaceSize").set((float) FACE_SIZE);
            backdropShader.safeGetUniform("Time").set(time);
            sky.apply(backdropShader);
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> backdropShader);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.vertex(-1.0, -1.0, 0.0).endVertex();
            builder.vertex(1.0, -1.0, 0.0).endVertex();
            builder.vertex(1.0, 1.0, 0.0).endVertex();
            builder.vertex(-1.0, 1.0, 0.0).endVertex();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            PortalViewRenderer.resumeScissor();
        }
        return atlas;
    }

    private static NativeImage noiseTable() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 256, 256, false);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int r = noiseByte(0, x, y);
                int g = noiseByte(0, x + 37, y + 17);
                int b = noiseByte(1, x, y);
                int a = noiseByte(1, x + 37, y + 17);
                image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
            }
        }
        return image;
    }

    private static int noiseByte(int table, int x, int y) {
        int h = table << 16 | (y & 255) << 8 | (x & 255);
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        h *= 0x846ca68b;
        h ^= h >>> 16;
        return h & 255;
    }
}
