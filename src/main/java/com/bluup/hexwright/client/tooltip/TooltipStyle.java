package com.bluup.hexwright.client.tooltip;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum TooltipStyle {
    COMMON("common", 1, 1),
    UNCOMMON("uncommon", 1, 1),
    RARE("rare", 1, 1),
    EPIC("epic", 1, 1),
    LEGENDARY("legendary", 15, 3),
    ARTIFACT("artifact", 8, 4);

    private static final int SPRITE = 100;
    private static final int BORDER = 30;
    private static final long MS_PER_TICK = 50L;

    public static final int PAD_SIDE = 13;
    public static final int PAD_BOTTOM = 12;
    public static final int PAD_TOP = 11;

    private final ResourceLocation background;
    private final ResourceLocation frame;
    private final int frameCount;
    private final int frameTicks;

    TooltipStyle(String name, int frameCount, int frameTicks) {
        this.background = Hexwright.id("textures/gui/tooltip/" + name + "_background.png");
        this.frame = Hexwright.id("textures/gui/tooltip/" + name + "_frame.png");
        this.frameCount = frameCount;
        this.frameTicks = frameTicks;
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height, int z) {
        int w = width + PAD_SIDE * 2;
        int h = height + PAD_TOP + PAD_BOTTOM;
        int left = x - PAD_SIDE;
        int top = y - PAD_TOP;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        nineSlice(graphics, background, left, top, w, h, 0, SPRITE);
        renderFrame(graphics, left, top, w, h);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private void renderFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        int sheetHeight = SPRITE * frameCount;
        if (frameCount == 1) {
            nineSlice(graphics, frame, x, y, width, height, 0, sheetHeight);
            return;
        }

        long period = MS_PER_TICK * frameTicks;
        long now = System.currentTimeMillis();
        int current = (int) ((now / period) % frameCount);
        int next = (current + 1) % frameCount;
        float blend = (now % period) / (float) period;

        nineSlice(graphics, frame, x, y, width, height, current * SPRITE, sheetHeight);
        if (blend > 0.0F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, blend);
            nineSlice(graphics, frame, x, y, width, height, next * SPRITE, sheetHeight);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void nineSlice(
        GuiGraphics graphics, ResourceLocation texture,
        int x, int y, int width, int height,
        int frameV, int textureHeight
    ) {
        int edgeX = Math.min(BORDER, width / 2);
        int edgeTop = Math.min(BORDER, height);
        int edgeBottom = Math.min(BORDER, height - edgeTop);
        int innerW = width - edgeX * 2;
        int innerH = height - edgeTop - edgeBottom;
        int srcInnerW = SPRITE - BORDER * 2;
        int srcInnerH = SPRITE - BORDER * 2;
        int rightU = SPRITE - edgeX;
        int bottomV = SPRITE - edgeBottom;
        int bottomY = y + height - edgeBottom;

        blit(graphics, texture, x, y, edgeX, edgeTop, 0, frameV, edgeX, edgeTop, textureHeight);
        blit(graphics, texture, x + width - edgeX, y, edgeX, edgeTop, rightU, frameV, edgeX, edgeTop, textureHeight);
        if (edgeBottom > 0) {
            blit(graphics, texture, x, bottomY, edgeX, edgeBottom, 0, frameV + bottomV, edgeX, edgeBottom, textureHeight);
            blit(graphics, texture, x + width - edgeX, bottomY, edgeX, edgeBottom,
                rightU, frameV + bottomV, edgeX, edgeBottom, textureHeight);
        }

        if (innerW > 0) {
            blit(graphics, texture, x + edgeX, y, innerW, edgeTop, BORDER, frameV, srcInnerW, edgeTop, textureHeight);
            if (edgeBottom > 0) {
                blit(graphics, texture, x + edgeX, bottomY, innerW, edgeBottom,
                    BORDER, frameV + bottomV, srcInnerW, edgeBottom, textureHeight);
            }
        }

        if (innerH > 0) {
            blit(graphics, texture, x, y + edgeTop, edgeX, innerH, 0, frameV + BORDER, edgeX, srcInnerH, textureHeight);
            blit(graphics, texture, x + width - edgeX, y + edgeTop, edgeX, innerH,
                rightU, frameV + BORDER, edgeX, srcInnerH, textureHeight);
        }

        if (innerW > 0 && innerH > 0) {
            blit(graphics, texture, x + edgeX, y + edgeTop, innerW, innerH,
                BORDER, frameV + BORDER, srcInnerW, srcInnerH, textureHeight);
        }
    }

    private static void blit(
        GuiGraphics graphics, ResourceLocation texture,
        int x, int y, int width, int height,
        int u, int v, int uWidth, int vHeight, int textureHeight
    ) {
        graphics.blit(texture, x, y, width, height, u, v, uWidth, vHeight, SPRITE, textureHeight);
    }
}
