package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.casting.actions.selectors.OpGetCaster;
import com.bluup.hexwright.server.reliquary.ChestCastEnv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(OpGetCaster.class)
public abstract class OpGetCasterChestMixin {

    @Inject(
        method = "execute",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$nameTheOpener(List<? extends Iota> args, CastingEnvironment env,
                                         CallbackInfoReturnable<List<Iota>> cir) {
        if (env instanceof ChestCastEnv chestEnv) {
            cir.setReturnValue(List.of(new EntityIota(chestEnv.opener())));
        }
    }
}
