package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.pentabox.PentaboxOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class PentaboxHotbarMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void hexwright$hideHotbarDuringOverlay(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (PentaboxOverlay.isActive()) {
            ci.cancel();
        }
    }
}
