package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.bluup.hexwright.server.constructshand.ConstructsGrasp;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentConstructsGraspMixin {

    @Inject(method = "getHeldItemToOperateOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexwright$offerConstructsHand(Predicate<ItemStack> stackOk,
                                               CallbackInfoReturnable<CastingEnvironment.HeldItemInfo> cir) {
        CastingEnvironment.HeldItemInfo lent = ConstructsGrasp.heldItem((CastingEnvironment) (Object) this, stackOk);
        if (lent != null) {
            cir.setReturnValue(lent);
        }
    }

    @ModifyExpressionValue(
        method = {"queryForMatchingStack", "withdrawItem"},
        at = @At(value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;getUsableStacks(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment$StackDiscoveryMode;)Ljava/util/List;"),
        remap = false
    )
    private List<ItemStack> hexwright$constructsHandFirst(List<ItemStack> usable) {
        return ConstructsGrasp.withHeldFirst((CastingEnvironment) (Object) this, usable);
    }
}
