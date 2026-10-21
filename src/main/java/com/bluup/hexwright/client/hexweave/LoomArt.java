package com.bluup.hexwright.client.hexweave;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
record LoomArt(ResourceLocation texture, int width, int height, float centreX, float centreY, boolean turned) {

    static LoomArt of(String name, int width, int height) {
        return new LoomArt(Hexwright.id("textures/gui/hex_loom/" + name + ".png"), width, height,
            width / 2f, height / 2f, false);
    }

    void draw(GuiGraphics graphics, float x, float y, float unit, float onScreen, int rgb, float brightness) {
        double texelPixels = onScreen / ThreadFrame.TEXELS_PER_UNIT * Minecraft.getInstance().getWindow().getGuiScale();
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(texelPixels < 1, false);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(unit / ThreadFrame.TEXELS_PER_UNIT, unit / ThreadFrame.TEXELS_PER_UNIT, 1f);
        if (turned) {
            pose.mulPose(Axis.ZP.rotationDegrees(90));
        }
        pose.translate(-centreX, -centreY, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float[] tint = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(tint[0] * brightness * (rgb >> 16 & 0xFF) / 255f,
            tint[1] * brightness * (rgb >> 8 & 0xFF) / 255f, tint[2] * brightness * (rgb & 0xFF) / 255f, tint[3]);
        graphics.blit(texture, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], tint[3]);
        pose.popPose();
    }
}
