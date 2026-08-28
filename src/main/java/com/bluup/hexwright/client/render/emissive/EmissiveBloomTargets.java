package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class EmissiveBloomTargets {
    @Nullable
    private static TextureTarget glow;
    @Nullable
    private static TextureTarget tight;
    @Nullable
    private static TextureTarget wide;
    @Nullable
    private static TextureTarget scratch;

    private static int wideWidth;
    private static int wideHeight;
    private static int attachedDepthTexture = -1;

    private EmissiveBloomTargets() {
    }

    static TextureTarget glow() {
        return glow;
    }

    static TextureTarget tight() {
        return tight;
    }

    static TextureTarget wide() {
        return wide;
    }

    static TextureTarget scratch() {
        return scratch;
    }

    static int wideWidth() {
        return wideWidth;
    }

    static int wideHeight() {
        return wideHeight;
    }

    static boolean ensure(RenderTarget main, int desiredWideWidth, int desiredWideHeight) {
        boolean glowResized = glow == null || glow.width != main.width || glow.height != main.height;
        if (glowResized) {
            glow = configure(glow, main.width, main.height);
        }

        int depthTexture = main.getDepthTextureId();
        if (depthTexture > 0 && (glowResized || attachedDepthTexture != depthTexture)) {
            attachDepth(depthTexture);
        }

        int tightWidth = Math.max(1, main.width / 2);
        int tightHeight = Math.max(1, main.height / 2);
        if (tight == null || tight.width != tightWidth || tight.height != tightHeight) {
            tight = configure(tight, tightWidth, tightHeight);
        }

        if (scratch == null || wide == null || wideWidth != desiredWideWidth || wideHeight != desiredWideHeight) {
            wideWidth = desiredWideWidth;
            wideHeight = desiredWideHeight;
            scratch = configure(scratch, desiredWideWidth, desiredWideHeight);
            wide = configure(wide, desiredWideWidth, desiredWideHeight);
        }

        return glow != null && tight != null && wide != null && scratch != null;
    }

    private static void attachDepth(int depthTexture) {
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, glow.frameBufferId);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
            GL11.GL_TEXTURE_2D, depthTexture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        boolean complete = status == GL30.GL_FRAMEBUFFER_COMPLETE && GL11.glGetError() == GL11.GL_NO_ERROR;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);

        attachedDepthTexture = complete ? depthTexture : -1;
        if (!complete) {
            Hexwright.LOGGER.warn("Emissive halo: could not share the scene depth buffer (status 0x{}); "
                + "the glow will not be occluded by anything in front of it", Integer.toHexString(status));
        }
    }

    private static TextureTarget configure(@Nullable TextureTarget existing, int width, int height) {
        TextureTarget target = existing;
        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        } else {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        target.setFilterMode(GL11.GL_LINEAR);
        target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        return target;
    }

    static void close() {
        if (glow != null) {
            glow.destroyBuffers();
            glow = null;
        }
        if (scratch != null) {
            scratch.destroyBuffers();
            scratch = null;
        }
        if (tight != null) {
            tight.destroyBuffers();
            tight = null;
        }
        if (wide != null) {
            wide.destroyBuffers();
            wide = null;
        }
        wideWidth = 0;
        wideHeight = 0;
        attachedDepthTexture = -1;
    }
}
