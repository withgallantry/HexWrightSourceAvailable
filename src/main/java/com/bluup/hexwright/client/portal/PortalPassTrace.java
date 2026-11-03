package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.render.IrisCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

public final class PortalPassTrace {

    private static final boolean ENABLED =
        "true".equals(System.getProperty("hexwright.portal.fbotrace"));

    private static final int MAX_LINES = 12;

    private static int lines;

    private PortalPassTrace() {
    }

    public static boolean enabled() {
        return ENABLED && lines < MAX_LINES;
    }

    public static void viewReadiness(String pane, int pass, RenderTarget target) {
        if (!ENABLED || (pass != 1 && pass != 60 && pass != 300 && pass != 1200)) return;
        Minecraft mc = Minecraft.getInstance();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        boolean swapBytes = GL11.glGetBoolean(GL11.GL_PACK_SWAP_BYTES);
        float[] sample = new float[1];
        float nearestDepth = 1;
        int writtenSamples = 0;
        int status;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, 0);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (status == GL30.GL_FRAMEBUFFER_COMPLETE) {
                for (int y = 1; y <= 3; y++) {
                    for (int x = 1; x <= 3; x++) {
                        GL11.glReadPixels(target.width * x / 4, target.height * y / 4, 1, 1,
                            GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, sample);
                        nearestDepth = Math.min(nearestDepth, sample[0]);
                        if (sample[0] < 1) writtenSamples++;
                    }
                }
            }
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, swapBytes ? 1 : 0);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPack);
        }
        Hexwright.LOGGER.info("[portal-readiness] pane={} pass={} dimension={} region=[{}] "
                + "visibleChunks={} depthSamples={}/9 nearestDepth={} fboStatus={} camera={} destination={} shaders={}",
            pane, pass, mc.level.dimension().location(),
            RemoteLevelManager.describeActiveRegion(PortalViewRenderer.activeDestinationCenter()),
            SodiumPortalCompat.diagnosticVisibleChunks(), writtenSamples, nearestDepth,
            Integer.toHexString(status), mc.gameRenderer.getMainCamera().getPosition(),
            PortalViewRenderer.activeDestinationCenter(), IrisCompat.isShaderPackActive());
    }

    public static void probe(String where, RenderTarget paneTarget, RenderTarget screenTarget) {
        if (!enabled()) {
            return;
        }
        lines++;

        int boundFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int attachment = colourAttachmentOf(boundFramebuffer);

        String owner;
        if (attachment == paneTarget.getColorTextureId()) {
            owner = "PANE (the swap reached this draw)";
        } else if (attachment == screenTarget.getColorTextureId()) {
            owner = "SCREEN (the swap did not reach this draw)";
        } else if (attachment == 0) {
            owner = "none - default framebuffer or no colour attachment";
        } else {
            owner = "SOMEONE ELSE (an Iris gbuffer or another mod's target)";
        }

        Hexwright.LOGGER.info(
            "[portal-fbo] {}: draw fbo={} colour0={} -> {} (pane colour={}, screen colour={}, "
                + "shaderpack={}, shadowPass={})",
            where, boundFramebuffer, attachment, owner,
            paneTarget.getColorTextureId(), screenTarget.getColorTextureId(),
            IrisCompat.isShaderPackActive(), IrisCompat.isRenderingShadowPass());
    }

    private static int colourAttachmentOf(int framebuffer) {
        if (framebuffer == 0) {
            return 0;
        }
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        int attachment = GL30.glGetFramebufferAttachmentParameteri(
            GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        return attachment;
    }

    public static void stage(String name, boolean ran) {
        if (!enabled()) {
            return;
        }
        lines++;
        Hexwright.LOGGER.info("[portal-fbo] pack stage in pass: {} {}", name, ran ? "RAN" : "cancelled");
    }

    public static void strategy(boolean irisPass) {
        if (!enabled()) {
            return;
        }
        lines++;
        String how;
        if (!irisPass) {
            how = "vanilla - swap the main render target for the pane's";
        } else {
            how = "Iris - destination pipeline renders and composites into the pane";
        }
        Hexwright.LOGGER.info("[portal-fbo] pass strategy: {} (main target fbo={})", how,
            Minecraft.getInstance().getMainRenderTarget().frameBufferId);
    }
}
