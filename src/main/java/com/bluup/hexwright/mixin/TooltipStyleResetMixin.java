package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.tooltip.GradedTooltips;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class TooltipStyleResetMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void hexwright$resetTooltipStyle(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GradedTooltips.clear();
    }
}
