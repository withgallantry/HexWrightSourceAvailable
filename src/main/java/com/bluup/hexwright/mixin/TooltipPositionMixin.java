package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.tooltip.GradedTooltips;
import com.bluup.hexwright.client.tooltip.TooltipStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.util.Mth;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphics.class)
public abstract class TooltipPositionMixin {
    @Redirect(
        method = "renderTooltipInternal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;"
                + "positionTooltip(IIIIII)Lorg/joml/Vector2ic;"
        )
    )
    private Vector2ic hexwright$positionGradedTooltip(
        ClientTooltipPositioner positioner,
        int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height
    ) {
        Vector2ic placed = positioner.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, width, height);
        if (GradedTooltips.peekStyle() == null) {
            return placed;
        }
        int minX = TooltipStyle.PAD_SIDE;
        int minY = TooltipStyle.PAD_TOP;
        int maxX = Math.max(minX, screenWidth - width - TooltipStyle.PAD_SIDE);
        int maxY = Math.max(minY, screenHeight - height - TooltipStyle.PAD_BOTTOM);
        return new Vector2i(Mth.clamp(placed.x(), minX, maxX), Mth.clamp(placed.y(), minY, maxY));
    }
}
