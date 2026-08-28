package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.signet.SignatureMark;
import com.bluup.hexwright.server.signet.SignatureTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Item.class)
public abstract class SignatureTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void hexwright$signatureTooltipImage(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        byte[] bits = SignatureMark.getBits(stack);
        if (bits != null) {
            cir.setReturnValue(Optional.of(new SignatureTooltip(bits)));
        }
    }
}
