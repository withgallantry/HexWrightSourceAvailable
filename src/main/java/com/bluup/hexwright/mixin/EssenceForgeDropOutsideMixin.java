package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.block.WorktableBlockEntity;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModularUIGuiContainer.class)
public abstract class EssenceForgeDropOutsideMixin {

    @Inject(method = "mouseReleased", at = @At("RETURN"), cancellable = true)
    private void hexwright$dropCarriedOutsideSlot(double mouseX, double mouseY, int button,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || (button != 0 && button != 1)) {
            return;
        }
        ModularUIGuiContainer self = (ModularUIGuiContainer) (Object) this;
        if (!(self.modularUI.holder instanceof WorktableBlockEntity)) {
            return;
        }
        if (self.getMenu().getCarried().isEmpty() || isOverSlot(self, mouseX, mouseY)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return;
        }
        client.gameMode.handleInventoryMouseClick(
            self.getMenu().containerId, -999, button, ClickType.PICKUP, client.player);
        cir.setReturnValue(true);
    }

    private static boolean isOverSlot(ModularUIGuiContainer screen, double mouseX, double mouseY) {
        double localX = mouseX - screen.modularUI.getGuiLeft();
        double localY = mouseY - screen.modularUI.getGuiTop();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.isActive()
                && localX >= slot.x - 1 && localX < slot.x + 17
                && localY >= slot.y - 1 && localY < slot.y + 17) {
                return true;
            }
        }
        return false;
    }
}
