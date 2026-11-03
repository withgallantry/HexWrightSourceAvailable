package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.tooltip.GradedTooltips;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipRenderUtil.class)
public abstract class TooltipBackgroundStyleMixin {
    @Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void hexwright$gradedTooltipBackground(
        GuiGraphics graphics, int x, int y, int width, int height, int z, CallbackInfo ci
    ) {
        if (GradedTooltips.renderBackground(graphics, x, y, width, height, z)) {
            ci.cancel();
        }
    }
}
