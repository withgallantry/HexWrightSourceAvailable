package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.pentabox.PentaboxLinkHighlight;
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

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void hexwright$drawLinkedFrame(GuiGraphics graphics, int x, int y, float partialTick, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (!PentaboxData.isLinkedStack(stack)) {
            return;
        }
        PentaboxLinkHighlight.draw(graphics, x, y);
    }
}
