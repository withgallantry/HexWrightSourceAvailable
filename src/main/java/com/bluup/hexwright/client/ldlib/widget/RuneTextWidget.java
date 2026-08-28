package com.bluup.hexwright.client.ldlib.widget;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public class RuneTextWidget extends Widget {
    private static final ResourceLocation RUNE_FONT = new ResourceLocation("minecraft", "alt");

    private static final int PADDING = 2;

    private Component source = Component.empty();
    private int color = 0xFFFFFFFF;
    private boolean dropShadow = true;

    private float maxScale = 1f;

    private String cachedText;
    private Component cachedStyled;

    public RuneTextWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public RuneTextWidget setSourceText(Component source) {
        this.source = source;
        this.cachedText = null;
        return this;
    }

    public RuneTextWidget setColor(int color) {
        this.color = color;
        return this;
    }

    public RuneTextWidget setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
        return this;
    }

    public RuneTextWidget setMaxScale(float maxScale) {
        this.maxScale = maxScale;
        return this;
    }

    private Component styled() {
        String resolved = source.getString();
        if (!resolved.equals(cachedText)) {
            cachedText = resolved;
            cachedStyled = Component.literal(resolved).withStyle(Style.EMPTY.withFont(RUNE_FONT));
        }
        return cachedStyled;
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);

        Component text = styled();
        if (text.getString().isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int boxWidth = getSize().width;
        int boxHeight = getSize().height;

        float scale = maxScale;
        if (textWidth > 0 && textWidth * scale > boxWidth - PADDING * 2) {
            scale = (boxWidth - PADDING * 2f) / textWidth;
        }

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(getPosition().x + boxWidth / 2f, getPosition().y + boxHeight / 2f, 0);
        pose.scale(scale, scale, 1f);
        graphics.drawString(font, text, -textWidth / 2, -font.lineHeight / 2, color, dropShadow);
        pose.popPose();
    }
}
