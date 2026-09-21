package com.bluup.hexwright.client.pentabox;

import com.lowdragmc.lowdraglib.gui.texture.ShaderTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public final class PentaboxLinkHighlight {

    private static final ResourceLocation GLOW = new ResourceLocation("ldlib", "compass_node_hover");
    private static final int FRAME = 0xFF4DA3D9;

    private PentaboxLinkHighlight() {
    }

    public static void draw(GuiGraphics graphics, int x, int y) {
        ShaderTexture glow = ShaderTexture.createShader(GLOW);
        if (glow == null || glow.getRawShader().isEmpty()) {
            drawFlatFrame(graphics, x, y);
            return;
        }
        glow.draw(graphics, 0, 0, x - 1, y - 1, 18, 18);
    }

    private static void drawFlatFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y, FRAME);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, FRAME);
        graphics.fill(x - 1, y, x, y + 16, FRAME);
        graphics.fill(x + 16, y, x + 17, y + 16, FRAME);
    }
}
