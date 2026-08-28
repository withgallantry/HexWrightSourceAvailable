package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.hexicon.HexiconOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HexiconHotbarMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void hexwright$hideHotbarDuringHexicon(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (HexiconOverlay.isActive()) {
            ci.cancel();
        }
    }
}
