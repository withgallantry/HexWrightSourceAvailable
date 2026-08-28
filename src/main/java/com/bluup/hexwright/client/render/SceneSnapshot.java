package com.bluup.hexwright.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SceneSnapshot {
    private static final Logger LOGGER = LoggerFactory.getLogger("hexwright-scene-snapshot");

    public static final int SLOT_PHOTON = 0;
    public static final int SLOT_PORTAL_PANE = 4;
    public static final int SLOT_SHIELD_CONTACT = 5;
    public static final int SLOT_VOID_TEAR = 6;
    private static final int SLOTS = 8;

    private static TextureTarget snapshot;
    private static long frameIndex;
    private static long capturedKey = Long.MIN_VALUE;
    private static boolean capturedColor;
    private static boolean depthBlitBroken;

    private SceneSnapshot() {
    }

    public static void register() {
        WorldRenderEvents.START.register(context -> frameIndex++);
    }

    public static int colorTextureId() {
        return snapshot == null ? 0 : snapshot.getColorTextureId();
    }

    public static int depthTextureId() {
        return snapshot == null ? 0 : snapshot.getDepthTextureId();
    }

    public static boolean depthAvailable() {
        return !depthBlitBroken;
    }

    public static boolean capture(int slot) {
        return capture(slot, true);
    }

    public static boolean captureDepthOnly(int slot) {
        return !depthBlitBroken && capture(slot, false);
    }

    private static boolean capture(int slot, boolean withColor) {
        if (!RenderSystem.isOnRenderThread()) {
            return false;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return false;
        }

        long key = frameIndex * SLOTS + slot;
        boolean sizeMatches = snapshot != null && snapshot.width == main.width && snapshot.height == main.height;
        if (key == capturedKey && sizeMatches && (capturedColor || !withColor)) {
            return true;
        }

        if (!sizeMatches) {
            if (snapshot == null) {
                snapshot = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                snapshot.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            } else {
                snapshot.resize(main.width, main.height, Minecraft.ON_OSX);
            }
            snapshot.setFilterMode(GL11.GL_LINEAR);
        }

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        com.bluup.hexwright.client.portal.PortalViewRenderer.suspendScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.frameBufferId);

        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        int mask = (withColor ? GL11.GL_COLOR_BUFFER_BIT : 0)
            | (depthBlitBroken ? 0 : GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height,
            0, 0, snapshot.width, snapshot.height, mask, GL11.GL_NEAREST);
        boolean depthLost = false;
        if (!depthBlitBroken && GL11.glGetError() != GL11.GL_NO_ERROR) {
            depthBlitBroken = true;
            depthLost = true;
            LOGGER.warn("Scene depth blit failed (incompatible main depth attachment?); "
                + "the depth snapshot will hold stale/undefined data, scene color unaffected");
            if (withColor) {
                GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height,
                    0, 0, snapshot.width, snapshot.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            }
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        com.bluup.hexwright.client.portal.PortalViewRenderer.resumeScissor();

        if (depthLost && !withColor) {
            return false;
        }
        capturedKey = key;
        capturedColor = withColor;
        return true;
    }
}
