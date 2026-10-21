package com.bluup.hexwright.client.hexweave;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.hexweave.ThreadDraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
final class ThreadFrame {

    private static final LoomArt FRAME = new LoomArt(Hexwright.id("textures/gui/hex_loom/thread.png"), 164, 177,
        82f, 88.5f, true);
    private static final LoomArt SELECTED = LoomArt.of("thread_selected", 212, 186);
    static final float TEXELS_PER_UNIT = 63f / (1.5f * ThreadDraft.RADIUS + 1f);

    static final float HALF_WIDTH = 88.5f / TEXELS_PER_UNIT;
    static final float HALF_HEIGHT = 78f / TEXELS_PER_UNIT;
    static final float ARMATURE = 80.5f / TEXELS_PER_UNIT;

    private ThreadFrame() {
    }

    static void draw(GuiGraphics graphics, float x, float y, float unit, float onScreen, float brightness) {
        FRAME.draw(graphics, x, y, unit, onScreen, 0xFFFFFF, brightness);
    }

    static void drawSelected(GuiGraphics graphics, float x, float y, float unit, float onScreen, int colour) {
        SELECTED.draw(graphics, x, y, unit, onScreen, colour, 1f);
    }
}
