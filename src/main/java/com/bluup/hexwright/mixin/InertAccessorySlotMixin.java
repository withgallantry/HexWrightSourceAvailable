package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.accessory.InertAccessorySlots;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class InertAccessorySlotMixin {

    private static final int HEXWRIGHT$INERT_SCRIM = 0xB4181818;

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void hexwright$greyInertSlots(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if (!InertAccessorySlots.isInertTalismanSlot(slot)) {
            return;
        }
        guiGraphics.fillGradient(RenderType.guiOverlay(), slot.x, slot.y, slot.x + 16, slot.y + 16,
            HEXWRIGHT$INERT_SCRIM, HEXWRIGHT$INERT_SCRIM, 0);
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void hexwright$explainInertSlot(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        if (this.hoveredSlot == null || !InertAccessorySlots.isInertTalismanSlot(this.hoveredSlot)) {
            return;
        }
        List<Component> lines = new ArrayList<>(cir.getReturnValue());
        lines.add(Component.translatable("tooltip.hexwright.talisman.slot_inert")
            .withStyle(ChatFormatting.RED));
        lines.add(Component.translatable("tooltip.hexwright.talisman.slot_inert.hint")
            .withStyle(ChatFormatting.DARK_GRAY));
        cir.setReturnValue(lines);
    }
}
