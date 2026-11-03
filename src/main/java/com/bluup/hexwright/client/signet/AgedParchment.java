package com.bluup.hexwright.client.signet;

import net.minecraft.client.gui.GuiGraphics;

public final class AgedParchment {
    private static final int FRAME_COLOR = 0xFF6E5830;

    private static final int BASE_LIGHT = 0xFFEEDFB6;
    private static final int BASE = 0xFFE2D19D;
    private static final int BASE_DARK = 0xFFD0BB84;
    private static final int EDGE = 0xFFAE9563;
    private static final int SPOT = 0xFF8F764B;

    private AgedParchment() {
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height, int scale) {
        graphics.fill(x - 2, y - 2, x + width * scale + 2, y + height * scale + 2, FRAME_COLOR);
        for (int gy = 0; gy < height; gy++) {
            for (int gx = 0; gx < width; gx++) {
                int px = x + gx * scale;
                int py = y + gy * scale;
                graphics.fill(px, py, px + scale, py + scale, cellColor(gx, gy, width, height));
            }
        }
    }

    private static int cellColor(int gx, int gy, int width, int height) {
        int edgeDist = Math.min(Math.min(gx, width - 1 - gx), Math.min(gy, height - 1 - gy));

        if (Math.floorMod(hash(gx, gy, 991), 53) == 0) {
            return SPOT;
        }
        if (edgeDist == 0) {
            return EDGE;
        }

        int shade = Math.floorMod(hash(gx, gy, 7919), 10);
        if (edgeDist == 1 && shade < 6) {
            return EDGE;
        }
        if (shade <= 1) {
            return BASE_LIGHT;
        }
        if (shade >= 8) {
            return BASE_DARK;
        }
        return BASE;
    }

    private static int hash(int x, int y, int salt) {
        int h = x * 374761393 + y * 668265263 + salt * 2038074743;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
}
