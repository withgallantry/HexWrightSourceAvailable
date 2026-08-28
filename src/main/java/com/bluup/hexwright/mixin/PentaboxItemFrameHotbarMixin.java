package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.pentabox.PentaboxData;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class PentaboxItemFrameHotbarMixin {
    private static final int FRAME = 0xFF4DA3D9;

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void hexwright$drawLinkedFrame(GuiGraphics graphics, int x, int y, float partialTick, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (!PentaboxData.isLinkedStack(stack)) {
            return;
        }
        graphics.fill(x - 1, y - 1, x + 17, y, FRAME);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, FRAME);
        graphics.fill(x - 1, y, x, y + 16, FRAME);
        graphics.fill(x + 16, y, x + 17, y + 16, FRAME);
    }
}
