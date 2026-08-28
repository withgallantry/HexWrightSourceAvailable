package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.tooltip.GradedTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class TooltipStackCaptureMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void hexwright$captureTooltipStack(
        Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir
    ) {
        GradedTooltips.capture((ItemStack) (Object) this);
    }
}
