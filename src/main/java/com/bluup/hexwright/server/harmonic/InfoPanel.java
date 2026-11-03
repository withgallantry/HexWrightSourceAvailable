package com.bluup.hexwright.server.harmonic;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class InfoPanel {

    static final float SCALE = 0.75f;
    static final int LINE_STEP = 8;

    private static final int PADDING = 5;

    private InfoPanel() {
    }

    record Line(String text, int color) {
    }

    static int visibleLines(int height) {
        return Math.max(1, (height - PADDING - 3) / LINE_STEP);
    }

    static int maxTextWidth(int width) {
        return (int) ((width - PADDING * 2) / SCALE);
    }

    static String clip(Font font, String text, int maxWidth) {
        return font.width(text) > maxWidth ? font.plainSubstrByWidth(text, maxWidth) : text;
    }

    static void draw(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        if (text.isEmpty()) {
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0f);
        pose.scale(SCALE, SCALE, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }
}
