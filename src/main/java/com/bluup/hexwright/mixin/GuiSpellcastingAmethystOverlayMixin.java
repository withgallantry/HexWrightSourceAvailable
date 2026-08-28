package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import com.bluup.hexwright.client.spellcasting.AugurStrokeGhostOverlay;
import com.bluup.hexwright.client.staff_assembly.AmethystGreatSpellOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiSpellcasting.class)
public abstract class GuiSpellcastingAmethystOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void hexwright$renderAmethystLearnedSpells(GuiGraphics graphics, int mouseX, int mouseY,
                                                        float partialTick, CallbackInfo ci) {
        AugurStrokeGhostOverlay.render(graphics, (GuiSpellcasting) (Object) this);

        InteractionHand hand = ((GuiSpellcastingAccessor) this).hexwright$getHandOpenedWith();
        Minecraft client = Minecraft.getInstance();
        AmethystGreatSpellOverlay.render(
            graphics,
            mouseX,
            mouseY,
            client.getWindow().getGuiScaledWidth(),
            client.getWindow().getGuiScaledHeight(),
            hand
        );
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hexwright$clickAmethystLearnedSpell(double mouseX, double mouseY, int button,
                                                      CallbackInfoReturnable<Boolean> cir) {
        InteractionHand hand = ((GuiSpellcastingAccessor) this).hexwright$getHandOpenedWith();
        Minecraft client = Minecraft.getInstance();
        if (AmethystGreatSpellOverlay.click(
            mouseX,
            mouseY,
            button,
            client.getWindow().getGuiScaledWidth(),
            client.getWindow().getGuiScaledHeight(),
            hand
        )) {
            cir.setReturnValue(true);
        }
    }
}
