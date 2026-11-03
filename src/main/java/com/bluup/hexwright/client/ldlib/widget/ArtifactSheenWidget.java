package com.bluup.hexwright.client.ldlib.widget;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class ArtifactSheenWidget extends Widget {
    private static final int WASH_RGB = 0xCA3837;
    private static final int GLINT_R = 255;
    private static final int GLINT_G = 132;
    private static final int GLINT_B = 118;

    private static final int WASH_ALPHA_MIN = 0x1C;
    private static final int WASH_ALPHA_MAX = 0x38;
    private static final float WASH_BREATH_MS = 3000f;

    private static final int GLINT_ALPHA = 0x80;
    private static final long SWEEP_EVERY_MS = 3200L;
    private static final long SWEEP_MS = 1100L;
    private static final float GLINT_HALF_WIDTH = 9f;
    private static final float GLINT_LEAN = 0.9f;

    private final long delayMs;

    public ArtifactSheenWidget(int x, int y, int width, int height, long delayMs) {
        super(x, y, width, height);
        this.delayMs = delayMs;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPosition().x;
        int y = getPosition().y;
        int width = getSize().width;
        int height = getSize().height;
        if (width <= 0 || height <= 0) {
            return;
        }

        long now = Util.getMillis() - delayMs;
        float breath = 0.5f + 0.5f * Mth.sin(now * Mth.TWO_PI / WASH_BREATH_MS);
        int washAlpha = Math.round(WASH_ALPHA_MIN + (WASH_ALPHA_MAX - WASH_ALPHA_MIN) * breath);
        graphics.fill(RenderType.guiOverlay(), x, y, x + width, y + height, washAlpha << 24 | WASH_RGB);

        long sweep = Math.floorMod(now, SWEEP_EVERY_MS);
        if (sweep < SWEEP_MS) {
            drawGlint(graphics, x, y, width, height, sweep / (float) SWEEP_MS);
        }
    }

    @Environment(EnvType.CLIENT)
    private static void drawGlint(GuiGraphics graphics, int x, int y, int width, int height, float progress) {
        float lean = height * GLINT_LEAN;
        float centre = -GLINT_HALF_WIDTH - lean / 2f + progress * (width + 2f * GLINT_HALF_WIDTH + lean);
        float slope = lean / height;

        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.guiOverlay());
        Matrix4f pose = graphics.pose().last().pose();
        for (int column = 0; column < width; column++) {
            float peak = (centre + lean / 2f - (column + 0.5f)) / slope;
            float reach = GLINT_HALF_WIDTH / slope;
            glintSpan(buffer, pose, x + column, y, height, peak - reach, peak, peak, reach);
            glintSpan(buffer, pose, x + column, y, height, peak, peak + reach, peak, reach);
        }
        graphics.flush();
    }

    @Environment(EnvType.CLIENT)
    private static void glintSpan(VertexConsumer buffer, Matrix4f pose, int x, int y, int height,
                                  float from, float to, float peak, float reach) {
        float top = Math.max(from, 0f);
        float bottom = Math.min(to, height);
        if (bottom <= top) {
            return;
        }
        int topAlpha = Math.round(GLINT_ALPHA * Math.max(0f, 1f - Math.abs(top - peak) / reach));
        int bottomAlpha = Math.round(GLINT_ALPHA * Math.max(0f, 1f - Math.abs(bottom - peak) / reach));
        buffer.vertex(pose, x, y + top, 0f).color(GLINT_R, GLINT_G, GLINT_B, topAlpha).endVertex();
        buffer.vertex(pose, x, y + bottom, 0f).color(GLINT_R, GLINT_G, GLINT_B, bottomAlpha).endVertex();
        buffer.vertex(pose, x + 1, y + bottom, 0f).color(GLINT_R, GLINT_G, GLINT_B, bottomAlpha).endVertex();
        buffer.vertex(pose, x + 1, y + top, 0f).color(GLINT_R, GLINT_G, GLINT_B, topAlpha).endVertex();
    }
}
