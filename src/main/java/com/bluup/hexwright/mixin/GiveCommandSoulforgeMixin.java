package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.soulforge.Soulforge;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GiveCommand.class)
public abstract class GiveCommandSoulforgeMixin {

    @ModifyExpressionValue(method = "giveItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/commands/arguments/item/ItemInput;createItemStack(IZ)Lnet/minecraft/world/item/ItemStack;",
        ordinal = 1))
    private static ItemStack hexwright$issueGiven(ItemStack stack, @Local(argsOnly = true) CommandSourceStack source) {
        Soulforge.onGiven(source.getServer(), stack);
        return stack;
    }
}
