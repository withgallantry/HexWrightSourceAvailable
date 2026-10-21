package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.bluup.hexwright.server.constructshand.ConstructsGrasp;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(targets = "at.petrak.hexcasting.common.casting.actions.spells.OpMakeBattery$Spell", remap = false)
public abstract class OpMakeBatteryConstructsGraspMixin {

    @WrapOperation(
        method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V",
        at = @At(value = "INVOKE", target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;replaceItem")
    )
    private boolean hexwright$replaceInConstructsHand(CastingEnvironment env, Predicate<ItemStack> stackOk,
                                                      ItemStack replaceWith, InteractionHand hand,
                                                      Operation<Boolean> original) {
        return ConstructsGrasp.replaceHeld(env, stackOk, replaceWith)
            || original.call(env, stackOk, replaceWith, hand);
    }
}
