package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.pentabox.PentaboxLinkHighlight;
import com.bluup.hexwright.server.pentabox.PentaboxData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class PentaboxItemFrameScreenMixin {

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void hexwright$drawLinkedFrame(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        ItemStack stack = slot.getItem();
        if (!PentaboxData.isLinkedStack(stack)) {
            return;
        }
        PentaboxLinkHighlight.draw(graphics, slot.x, slot.y);
    }
}
