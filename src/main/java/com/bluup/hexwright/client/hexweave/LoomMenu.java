package com.bluup.hexwright.client.hexweave;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
final class LoomMenu {

    private static final int BORDER = 4;
    private static final int TITLE = 16;
    private static final int GAP = 2;
    private static final int PAD = 4;

    static final int CHROME_WIDTH = 2 * (BORDER + PAD);
    static final int CHROME_HEIGHT = BORDER + TITLE + GAP + 2 * PAD + BORDER;

    record Look(@Nullable IGuiTexture panel, @Nullable IGuiTexture title, @Nullable IGuiTexture well,
                @Nullable IGuiTexture row, @Nullable IGuiTexture hoveredRow) {
    }

    private LoomMenu() {
    }

    static int contentX(int x) {
        return x + BORDER + PAD;
    }

    static int contentY(int y) {
        return y + BORDER + TITLE + GAP + PAD;
    }

    static void drawFrame(GuiGraphics graphics, Look look, int mouseX, int mouseY, int x, int y, int width, int height,
                          Component title, int titleColour) {
        Font font = Minecraft.getInstance().font;
        int inner = width - 2 * BORDER;
        draw(look.panel(), graphics, mouseX, mouseY, x, y, width, height);
        draw(look.title(), graphics, mouseX, mouseY, x + BORDER, y + BORDER, inner, TITLE);
        int wellTop = y + BORDER + TITLE + GAP;
        draw(look.well(), graphics, mouseX, mouseY, x + BORDER, wellTop, inner, y + height - BORDER - wellTop);
        graphics.drawString(font, title, x + BORDER + PAD, y + BORDER + (TITLE - font.lineHeight) / 2 + 1, titleColour);
    }

    static void drawRow(GuiGraphics graphics, Look look, int mouseX, int mouseY, int x, int y, int width, int height,
                        boolean hovered) {
        draw(hovered && look.hoveredRow() != null ? look.hoveredRow() : look.row(), graphics, mouseX, mouseY,
            x, y, width, height);
    }

    static void draw(@Nullable IGuiTexture texture, GuiGraphics graphics, int mouseX, int mouseY,
                     int x, int y, int width, int height) {
        if (texture != null) {
            texture.draw(graphics, mouseX, mouseY, x, y, width, height);
        }
    }
}
