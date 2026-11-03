package com.bluup.hexwright.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SolidDepthSnapshot {
    private static final Logger LOGGER = LoggerFactory.getLogger("hexwright-solid-depth");

    private static TextureTarget target;
    private static boolean broken;
    private static boolean valid;

    private SolidDepthSnapshot() {
    }

    public static int depthTextureId() {
        return valid && target != null ? target.getDepthTextureId() : 0;
    }

    public static boolean capture() {
        valid = false;
        if (broken || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return false;
        }

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }

        com.bluup.hexwright.client.portal.PortalViewRenderer.suspendScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);

        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        RenderSystem.depthMask(true);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height,
            0, 0, target.width, target.height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        boolean failed = GL11.glGetError() != GL11.GL_NO_ERROR;

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        com.bluup.hexwright.client.portal.PortalViewRenderer.resumeScissor();

        if (failed) {
            broken = true;
            LOGGER.warn("Solid depth blit failed (incompatible main depth attachment?); "
                + "effects masking against opaque terrain are disabled for this session");
            return false;
        }

        valid = true;
        return true;
    }
}
