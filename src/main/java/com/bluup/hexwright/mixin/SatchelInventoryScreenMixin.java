package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.reliquary.SatchelBackpackInventoryOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public abstract class SatchelInventoryScreenMixin {

    @Shadow @Final protected Set<Slot> quickCraftSlots;
    @Shadow protected boolean isQuickCrafting;
    @Shadow private boolean skipNextRelease;

    @Shadow public abstract void clearDraggingState();

    @Shadow
    private @Nullable Slot findSlot(double mouseX, double mouseY) {
        throw new AssertionError("mixin stub");
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void hexwright$renderBackpackSatchelOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SatchelBackpackInventoryOverlay.render((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hexwright$satchelOverlayMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (SatchelBackpackInventoryOverlay.mouseClicked((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void hexwright$satchelOverlayMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Slot hovered = this.findSlot(mouseX, mouseY);
        ItemStack carried = Minecraft.getInstance().player == null
            ? ItemStack.EMPTY
            : Minecraft.getInstance().player.containerMenu.getCarried();
        boolean claimed = SatchelBackpackInventoryOverlay.mouseReleased(
            (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY, button,
            hovered == null ? -1 : hovered.index, carried);
        if (!claimed) {
            return;
        }
        this.isQuickCrafting = false;
        this.quickCraftSlots.clear();
        this.skipNextRelease = false;
        this.clearDraggingState();
        cir.setReturnValue(true);
    }
}
