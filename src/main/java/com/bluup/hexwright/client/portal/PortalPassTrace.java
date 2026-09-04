package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.render.IrisCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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

    public static void strategy(boolean irisPass) {
        if (!enabled()) {
            return;
        }
        lines++;
        Hexwright.LOGGER.info("[portal-fbo] pass strategy: {} (main target fbo={})",
            irisPass ? "Iris - render into the screen target and copy the result out"
                : "vanilla - swap the main render target for the pane's",
            Minecraft.getInstance().getMainRenderTarget().frameBufferId);
    }
}
