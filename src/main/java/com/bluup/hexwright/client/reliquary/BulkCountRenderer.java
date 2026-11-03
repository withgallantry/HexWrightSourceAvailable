package com.bluup.hexwright.client.reliquary;

import com.bluup.hexwright.server.reliquary.BulkCount;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class BulkCountRenderer {

    private static final float SCALE = 0.7f;

    private static final int CORNER = 17;
    private static final int ROW_HEIGHT = 8;

    private BulkCountRenderer() {
    }

    public static void drawCount(GuiGraphics graphics, Font font, long count, int x, int y) {
        String text = BulkCount.format(count);
        int width = font.width(text);
        float scale = Math.min(SCALE, (float) CORNER / width);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + CORNER - width * scale, y + CORNER - ROW_HEIGHT * scale, 200);
        pose.scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
        pose.popPose();
    }

    public static void renderDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!BulkCount.isBulk(stack.getCount())) {
            graphics.renderItemDecorations(font, stack, x, y);
            return;
        }
        graphics.renderItemDecorations(font, stack, x, y, "");
        drawCount(graphics, font, stack.getCount(), x, y);
    }

    public static void renderTooltip(GuiGraphics graphics, Font font, ItemStack stack, int mouseX, int mouseY) {
        if (!BulkCount.isBulk(stack.getCount())) {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
        lines.add(BulkCount.exactLine(stack.getCount()));
        graphics.renderTooltip(font, lines, stack.getTooltipImage(), mouseX, mouseY);
    }
}
