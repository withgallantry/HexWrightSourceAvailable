package com.bluup.hexwright.server.research;

import com.bluup.hexwright.Hexwright;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class ResearchArt {

    static final ResourceLocation SLAB = Hexwright.id("textures/gui/research/slab.png");
    static final int SLAB_TEX_W = 144;
    static final int SLAB_TEX_H = 179;
    static final ResourceLocation HOLLOWS = Hexwright.id("textures/gui/research/hollows.png");
    static final ResourceLocation FLUID = Hexwright.id("textures/gui/research/inspiration.png");
    private static final ResourceLocation CARD = Hexwright.id("textures/gui/research/card.png");
    private static final ResourceLocation RUNES = Hexwright.id("textures/gui/research/runes.png");

    static final int CELL = 16;
    static final int PITCH_X = 17;
    static final int PITCH_Y = 20;

    static final int CARD_H = 30;
    private static final int CARD_TEX_W = 140;
    private static final int CARD_END = 6;
    private static final int CARD_PAD = 7;

    static final IGuiTexture BOX = new ResourceBorderTexture("ldlib:textures/menu/list-box.png", 180, 180, 4, 4);
    static final IGuiTexture BOX_LIT = new ResourceBorderTexture("ldlib:textures/menu/list-box-select.png", 180, 180, 4, 4);
    static final IGuiTexture DESK = new ResourceBorderTexture("hexwright:textures/gui/research/leather.png", 182, 100, 6, 6);

    static final int GOLD = 0xFFE2B98A;
    static final int PALE = 0xFFC6B4C8;
    static final int DIM = 0xFF7E6C82;
    static final int LIT = 0x4CD6FF;
    static final int SHORT = 0xFFD86A64;
    static final int LOCKED = 0x555F6B;
    static final int ARTIFACT_STONE = 0xFFE4DE;
    static final int TROUGH = 0xC0140E1C;
    static final int TROUGH_EDGE = 0xFF3A2E44;
    private static final int CARVED = 0xC4BAAA;
    private static final int CARVED_SHADOW = 0x0E0B0D;
    private static final int INK = 0x3A2822;
    private static final int LIT_CORE = 0xEBFCFF;

    private ResearchArt() {
    }

    static int cardWidth(TabletPuzzle.Scrap scrap) {
        return scrap.glyphs().length * PITCH_X + CARD_PAD * 2;
    }

    @Environment(EnvType.CLIENT)
    static void drawCard(GuiGraphics graphics, TabletPuzzle.Scrap scrap, int x, int y) {
        int width = cardWidth(scrap);
        graphics.blit(CARD, x, y, 0, 0, width - CARD_END, CARD_H, CARD_TEX_W, CARD_H);
        graphics.blit(CARD, x + width - CARD_END, y, CARD_TEX_W - CARD_END, 0, CARD_END, CARD_H, CARD_TEX_W, CARD_H);

        int runEnd = scrap.runStart() + scrap.runLength();
        for (int i = 0; i < scrap.glyphs().length; i++) {
            int gx = x + CARD_PAD + i * PITCH_X;
            int gy = y + (CARD_H - CELL) / 2;
            if (i >= scrap.runStart() && i < runEnd) {
                light(graphics, scrap.glyphs()[i], gx, gy);
            } else {
                rune(graphics, scrap.glyphs()[i], gx, gy, INK, 0.85f);
                graphics.fill(gx, y + CARD_H - 4, gx + CELL, y + CARD_H - 3, 0xC0785C3C);
            }
        }
    }

    @Environment(EnvType.CLIENT)
    static void carve(GuiGraphics graphics, int glyph, int x, int y, float strength) {
        rune(graphics, glyph, x + 1, y + 1, CARVED_SHADOW, 0.85f * strength);
        rune(graphics, glyph, x, y, CARVED, 0.80f * strength);
    }

    @Environment(EnvType.CLIENT)
    static void light(GuiGraphics graphics, int glyph, int x, int y) {
        for (int[] offset : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            rune(graphics, glyph, x + offset[0], y + offset[1], LIT, 0.35f);
        }
        rune(graphics, glyph, x, y, LIT, 1f);
        rune(graphics, glyph, x, y, LIT_CORE, 0.55f);
    }

    @Environment(EnvType.CLIENT)
    private static void rune(GuiGraphics graphics, int glyph, int x, int y, int colour, float alpha) {
        int index = Mth.clamp(glyph, 0, TabletPuzzle.ALPHABET - 1);
        tint(graphics, colour, alpha);
        graphics.blit(RUNES, x, y, CELL, CELL, (index % 4) * CELL, (index / 4) * CELL, CELL, CELL, 64, 64);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    @Environment(EnvType.CLIENT)
    static void tint(GuiGraphics graphics, int colour, float alpha) {
        graphics.setColor(((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f, alpha);
    }

    static String clock(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
