package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.MishapEnvironment;
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv;
import com.bluup.hexwright.server.armour.ArmourPowerToggle;
import com.bluup.hexwright.server.armour.ArmourSet;
import com.bluup.hexwright.server.armour.ArmourTier;
import com.bluup.hexwright.server.armour.HexwardenMishapEnv;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerBasedCastEnv.class)
public abstract class HexwardenMishapMixin {

    @Inject(
        method = "getMishapEnvironment",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void hexwright$wardMishaps(CallbackInfoReturnable<MishapEnvironment> cir) {
        ServerPlayer caster = ((PlayerBasedCastEnv) (Object) this).getCaster();
        if (caster == null) {
            return;
        }
        ArmourTier tier = ArmourPowerToggle.activeTier(caster, ArmourSet.HEXWARDEN);
        if (tier != null) {
            cir.setReturnValue(new HexwardenMishapEnv(cir.getReturnValue(), caster, tier));
        }
    }
}
