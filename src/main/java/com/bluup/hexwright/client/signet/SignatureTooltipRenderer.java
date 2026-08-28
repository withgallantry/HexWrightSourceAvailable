package com.bluup.hexwright.client.signet;

import com.bluup.hexwright.server.signet.SignatureMark;
import com.bluup.hexwright.server.signet.SignatureTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public final class SignatureTooltipRenderer implements ClientTooltipComponent {
    private static final int SCALE = 2;
    private static final int INK_COLOR = 0xFF2B2013;

    private final byte[] bits;

    public SignatureTooltipRenderer(SignatureTooltip data) {
        this.bits = data.bits();
    }

    @Override
    public int getHeight() {
        return SignatureMark.HEIGHT * SCALE + 4;
    }

    @Override
    public int getWidth(Font font) {
        return SignatureMark.WIDTH * SCALE;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        AgedParchment.render(graphics, x, y, SignatureMark.WIDTH, SignatureMark.HEIGHT, SCALE);

        for (int gy = 0; gy < SignatureMark.HEIGHT; gy++) {
            for (int gx = 0; gx < SignatureMark.WIDTH; gx++) {
                if (SignatureMark.isSet(this.bits, gx, gy)) {
                    int px = x + gx * SCALE;
                    int py = y + gy * SCALE;
                    graphics.fill(px, py, px + SCALE, py + SCALE, INK_COLOR);
                }
            }
        }
    }
}
