package com.bluup.hexwright.server.flourish.workbench;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Rarity;

public final class WorkbenchArt {

    public static final int GOLD = 0xFFE2B98A;
    public static final int PALE = 0xFFC6B4C8;
    public static final int DIM = 0xFF7E6C82;
    public static final int REFUSED = 0xFFD86A64;
    static final int WELL = 0xFF2A1A24;
    static final int EDGE = 0xFF000000;
    static final int LIT_FILL = 0xFF4E3D4A;
    static final int SHEET_RIM = 0xFF604C5E;
    static final int SHEET_FILL = 0xFF3D2F3D;
    static final int RULE = 0x807E6C82;
    static final int APART_RIM = 0xFF5C6A9A;
    static final int APART = 0xFF2C3052;
    static final int APART_LIT = 0xFF3C426C;

    private static final String ELLIPSIS = "\u2026";

    private WorkbenchArt() {
    }

    static IGuiTexture panel(String name) {
        return new ResourceBorderTexture("ldlib:textures/" + name + ".png", 180, 180, 4, 4);
    }

    public static IGuiTexture box() {
        return panel("menu/list-box");
    }

    public static IGuiTexture boxLit() {
        return panel("menu/list-box-select");
    }

    static IGuiTexture sheet() {
        return new ResourceBorderTexture("hexwright:textures/gui/flourish_workbench/sheet.png", 180, 180, 4, 4);
    }

    static int rarityColour(Rarity rarity) {
        Integer colour = rarity.color.getColor();
        return colour != null ? 0xFF000000 | colour : PALE;
    }

    @Environment(EnvType.CLIENT)
    public static void centred(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence line = fit(font, text, width);
        graphics.drawString(font, line, x + (width - font.width(line)) / 2, y, colour, false);
    }

    @Environment(EnvType.CLIENT)
    public static void left(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, fit(font, text, width), x, y, colour, false);
    }

    @Environment(EnvType.CLIENT)
    static void right(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence line = fit(font, text, width);
        graphics.drawString(font, line, x + width - font.width(line), y, colour, false);
    }

    @Environment(EnvType.CLIENT)
    static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width(ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, Component.literal(ELLIPSIS)));
    }

    @Environment(EnvType.CLIENT)
    static boolean fits(Component text, int width) {
        return Minecraft.getInstance().font.width(text) <= width;
    }

    @Environment(EnvType.CLIENT)
    static void refill(GuiGraphics graphics, int x, int y, int width, int height, int rim, int fill) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, rim);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
    }

    @Environment(EnvType.CLIENT)
    public static void edge(GuiGraphics graphics, int x, int y, int width, int height, int colour) {
        graphics.fill(x + 1, y, x + width - 1, y + 1, colour);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, colour);
        graphics.fill(x, y + 1, x + 1, y + height - 1, colour);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, colour);
    }

    @Environment(EnvType.CLIENT)
    static void disc(GuiGraphics graphics, int x, int y, int size, int colour) {
        int centre = size / 2;
        double radius = size / 2.0 - 0.1;
        for (int row = 0; row < size; row++) {
            int dy = row - centre;
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(x + centre - half, y + row, x + centre + half + 1, y + row + 1, colour);
        }
    }

    public static final int LOCK = 7;

    @Environment(EnvType.CLIENT)
    public static void lock(GuiGraphics graphics, int x, int y, int colour) {
        graphics.fill(x + 2, y, x + 5, y + 1, colour);
        graphics.fill(x + 1, y + 1, x + 2, y + 3, colour);
        graphics.fill(x + 5, y + 1, x + 6, y + 3, colour);
        graphics.fill(x, y + 3, x + LOCK, y + LOCK, colour);
        graphics.fill(x + 3, y + 4, x + 4, y + 6, WELL);
    }
}
