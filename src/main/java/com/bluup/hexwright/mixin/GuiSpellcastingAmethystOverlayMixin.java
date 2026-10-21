package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import com.bluup.hexwright.client.spellcasting.AugurStrokeGhostOverlay;
import com.bluup.hexwright.client.staff_assembly.AmethystGreatSpellOverlay;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiSpellcasting.class)
public abstract class GuiSpellcastingAmethystOverlayMixin extends Screen {
    @Unique
    private @Nullable HexPattern hexwright$carriedGreatSpell;

    @Unique
    private @Nullable EditBox hexwright$greatSpellSearch;

    protected GuiSpellcastingAmethystOverlayMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hexwright$addGreatSpellSearch(CallbackInfo ci) {
        String previous = hexwright$greatSpellSearch == null ? "" : hexwright$greatSpellSearch.getValue();
        hexwright$greatSpellSearch = AmethystGreatSpellOverlay.createSearch(this.font, this.width, previous);
        this.addWidget(hexwright$greatSpellSearch);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hexwright$tickGreatSpellSearch(CallbackInfo ci) {
        if (hexwright$greatSpellSearch != null) {
            hexwright$greatSpellSearch.tick();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void hexwright$renderAmethystLearnedSpells(GuiGraphics graphics, int mouseX, int mouseY,
                                                        float partialTick, CallbackInfo ci) {
        GuiSpellcasting spellGui = (GuiSpellcasting) (Object) this;
        AugurStrokeGhostOverlay.render(graphics, spellGui);

        if (hexwright$greatSpellSearch == null) {
            return;
        }
        InteractionHand hand = ((GuiSpellcastingAccessor) this).hexwright$getHandOpenedWith();
        AmethystGreatSpellOverlay.render(graphics, spellGui, mouseX, mouseY, hand, hexwright$carriedGreatSpell,
            hexwright$greatSpellSearch, partialTick);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hexwright$clickAmethystLearnedSpell(double mouseX, double mouseY, int button,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (hexwright$greatSpellSearch == null) {
            return;
        }
        if (hexwright$greatSpellSearch.isMouseOver(mouseX, mouseY)) {
            return;
        }
        this.setFocused(null);

        GuiSpellcasting spellGui = (GuiSpellcasting) (Object) this;
        InteractionHand hand = ((GuiSpellcastingAccessor) this).hexwright$getHandOpenedWith();

        AmethystGreatSpellOverlay.Picked picked = button == 0
            ? AmethystGreatSpellOverlay.pick(mouseX, mouseY, spellGui, hand, hexwright$greatSpellSearch)
            : null;
        if (picked != null) {
            if (Screen.hasShiftDown()) {
                hexwright$carriedGreatSpell = null;
                HexwrightNetworking.sendAmethystGreatSpellPush(hand, picked.index());
            } else {
                hexwright$carriedGreatSpell = picked.pattern();
            }
            cir.setReturnValue(true);
            return;
        }

        if (hexwright$carriedGreatSpell != null) {
            if (button != 0
                || AmethystGreatSpellOverlay.place(spellGui, hand, hexwright$carriedGreatSpell, mouseX, mouseY)) {
                hexwright$carriedGreatSpell = null;
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void hexwright$dropCarriedGreatSpell(CallbackInfo ci) {
        if (hexwright$carriedGreatSpell != null) {
            hexwright$carriedGreatSpell = null;
            ci.cancel();
        }
    }
}
