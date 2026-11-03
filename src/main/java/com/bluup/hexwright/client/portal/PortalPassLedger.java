package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.render.IrisCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;

final class PortalPassLedger {

    record Reading(int framebuffer, int passDepth, boolean remotePass, boolean privatePass,
                   double nearPlane, boolean scissor) {
    }

    private static String lastLeak;

    private PortalPassLedger() {
    }

    static Reading read() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        return new Reading(
            main == null ? -1 : main.frameBufferId,
            PortalViewRenderer.passDepthForLedger(),
            RemoteLevelManager.isRemotePassActive(),
            IrisCompat.isPrivatePassActive(),
            PortalViewRenderer.activeNearPlane(),
            PortalViewRenderer.scissorActiveForLedger());
    }

    static void verify(Reading before) {
        Reading after = read();
        String leak = null;
        if (after.passDepth() != before.passDepth()) {
            leak = "pass depth (" + before.passDepth() + " -> " + after.passDepth()
                + "); a pass did not unwind, and nesting will be wrong from here";
        } else if (after.remotePass() != before.remotePass()) {
            leak = "the remote-level swap is still active; every later frame will refuse to render "
                + "a pane and they will stay shimmer until the level is reloaded";
        } else if (after.privatePass() != before.privatePass()) {
            leak = "the private-pass counter is unbalanced; the shader pack will stay stood down "
                + "and the world will render with vanilla programs";
        } else if (after.framebuffer() != before.framebuffer()) {
            leak = "the main render target (" + before.framebuffer() + " -> " + after.framebuffer()
                + "); the frame is drawing into a pane's buffer";
        } else if (after.scissor() != before.scissor()) {
            leak = "the scissor is still set; the frame will be banded with stale content";
        } else if (after.nearPlane() != before.nearPlane()) {
            leak = "the pack's near-plane override is still set (" + after.nearPlane()
                + "); depth-derived effects will be wrong for the whole frame";
        }

        if (leak == null) {
            lastLeak = null;
            return;
        }
        if (!leak.equals(lastLeak)) {
            lastLeak = leak;
            Hexwright.LOGGER.error("[portal] a pass did not put the frame back: {}", leak);
        }
    }
}
