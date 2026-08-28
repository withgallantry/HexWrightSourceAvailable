package com.bluup.hexwright.mixin;

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
    private static final int FRAME = 0xFF4DA3D9;

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void hexwright$drawLinkedFrame(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        ItemStack stack = slot.getItem();
        if (!PentaboxData.isLinkedStack(stack)) {
            return;
        }

        int x = slot.x;
        int y = slot.y;
        graphics.fill(x - 1, y - 1, x + 17, y, FRAME);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, FRAME);
        graphics.fill(x - 1, y, x, y + 16, FRAME);
        graphics.fill(x + 16, y, x + 17, y + 16, FRAME);
    }
}
