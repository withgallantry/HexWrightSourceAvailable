package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.common.staff_assembly.calc.ComponentConfig;
import com.bluup.hexwright.common.staff_assembly.calc.ComponentResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StaffInfoPanelRenderer {
    public static final int COLOR_TEXT            = 0xC8D7FF;
    public static final int COLOR_DELTA_POSITIVE  = 0xFF55CC55;
    public static final int COLOR_DELTA_NEGATIVE  = 0xFFFF5555;
    public static final int COLOR_BAR_BG          = 0xFF0E1014;
    public static final int LINE_GAP              = 1;

    private StaffInfoPanelRenderer() {
    }

    public static void renderLargeItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return;
        }
        float scale = size / 16f;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    public static void drawScaledString(GuiGraphics graphics, Font font, String text, int x, int y, int color, float scale) {
        scale = effectiveScale(scale);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    public static void drawCenteredString(GuiGraphics graphics, Font font, String text, int areaX, int areaWidth, int y, int color, float scale) {
        scale = effectiveScale(scale);
        int x = areaX + (int) ((areaWidth - font.width(text) * scale) / 2);
        drawScaledString(graphics, font, text, x, y, color, scale);
    }

    public static float effectiveScale(float scale) {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        if (guiScale <= 0) {
            return scale;
        }
        double total = guiScale * scale;
        double snappedTotal = Math.max(0.5, Math.round(total * 2) / 2.0);
        return (float) (snappedTotal / guiScale);
    }

    public static void renderBar(GuiGraphics graphics, int x, int y, int width, int height, double fraction, int fillColor) {
        graphics.fill(x, y, x + width, y + height, COLOR_BAR_BG);
        int filled = (int) Math.round(width * fraction);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, fillColor);
        }
    }

    public static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public static String format(double value) {
        return String.valueOf(Math.round(value));
    }

    public static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    public static List<String> wrapLines(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    public static int drawComponentLine(GuiGraphics graphics, Font font, int textX, int textY, int textWidth, float scale, ComponentConfig config, ComponentResult merged, ComponentResult live, ComponentResult persistedComponent, boolean showDeltas) {
        long delta = Math.round(merged.output() - persistedComponent.output());
        if (showDeltas && live.itemCount() > 0 && delta != 0) {
            return drawChangeLine(graphics, font, textX, textY, textWidth, scale, config.statLabel(), format(persistedComponent.output()), format(merged.output()), delta);
        }
        return drawStatLine(graphics, font, textX, textY, textWidth, config.statLabel(), format(merged.output()), scale);
    }

    public static int drawStatLine(GuiGraphics graphics, Font font, int textX, int textY, int textWidth, String label, String value, float scale) {
        drawScaledString(graphics, font, label, textX, textY, COLOR_TEXT, scale);
        int valueWidth = (int) (font.width(value) * scale);
        drawScaledString(graphics, font, value, textX + textWidth - valueWidth, textY, COLOR_TEXT, scale);
        return textY + (int) (font.lineHeight * scale) + LINE_GAP;
    }

    public static int drawChangeLine(GuiGraphics graphics, Font font, int textX, int textY, int textWidth, float scale, String label, String oldValue, String newValue, long delta) {
        int color = delta > 0 ? COLOR_DELTA_POSITIVE : COLOR_DELTA_NEGATIVE;

        drawScaledString(graphics, font, label, textX, textY, COLOR_TEXT, scale);

        String prefix = oldValue + " → ";
        String deltaText = " (" + (delta > 0 ? "+" : "") + delta + ")";
        int blockWidth = (int) (font.width(prefix) * scale) + (int) (font.width(newValue) * scale) + (int) (font.width(deltaText) * scale);
        int x = textX + textWidth - blockWidth;

        drawScaledString(graphics, font, prefix, x, textY, COLOR_TEXT, scale);
        x += (int) (font.width(prefix) * scale);

        drawScaledString(graphics, font, newValue, x, textY, color, scale);
        x += (int) (font.width(newValue) * scale);

        drawScaledString(graphics, font, deltaText, x, textY, color, scale);

        return textY + (int) (font.lineHeight * scale) + LINE_GAP;
    }

    public static int drawLineWithDelta(GuiGraphics graphics, Font font, int textX, int textY, int textWidth, float scale, String label, String value, double delta, boolean showDelta, boolean asPercent) {
        drawScaledString(graphics, font, label, textX, textY, COLOR_TEXT, scale);

        long rounded = showDelta ? Math.round(asPercent ? delta * 100 : delta) : 0;
        String deltaText = rounded != 0 ? " (" + (rounded > 0 ? "+" : "") + rounded + (asPercent ? "%" : "") + ")" : "";
        int color = rounded > 0 ? COLOR_DELTA_POSITIVE : COLOR_DELTA_NEGATIVE;

        int valueWidth = (int) (font.width(value) * scale);
        int deltaWidth = (int) (font.width(deltaText) * scale);
        int x = textX + textWidth - valueWidth - deltaWidth;

        drawScaledString(graphics, font, value, x, textY, COLOR_TEXT, scale);
        if (!deltaText.isEmpty()) {
            drawScaledString(graphics, font, deltaText, x + valueWidth, textY, color, scale);
        }

        return textY + (int) (font.lineHeight * scale) + LINE_GAP;
    }
}
