package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.common.casting.actions.rw.OpRead;
import com.bluup.hexwright.server.weapon.WebBallItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(OpRead.class)
public abstract class OpReadWebBallMixin {

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$readWebBall(List<? extends Iota> args, CastingEnvironment env,
                                       CallbackInfoReturnable<List<Iota>> cir) {
        CastingEnvironment.HeldItemInfo held = env.getHeldItemToOperateOn(stack -> stack.getItem() instanceof WebBallItem);
        if (held == null) {
            return;
        }
        Iota caught = WebBallItem.read(held.stack(), env.getWorld());
        if (caught == null) {
            throw MishapBadOffhandItem.of(held.stack(), "iota.read");
        }
        cir.setReturnValue(List.of(caught));
    }
}
