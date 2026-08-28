package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import com.bluup.hexwright.server.block.ResonantFieldRangeComponent;
import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import com.bluup.hexwright.server.staff_assembly.StaffCastAnimationTrigger;
import com.bluup.hexwright.server.staff_assembly.StaffPowerCastEnv;
import com.bluup.hexwright.server.talisman.TalismanCastEnv;
import com.bluup.hexwright.server.talisman.TalismanCasting;
import com.bluup.hexwright.server.talisman.TalismanData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StaffCastEnv.class)
public abstract class StaffCastEnvMixin {

    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void hexwright$attachResonantField(ServerPlayer caster, InteractionHand hand, CallbackInfo ci) {
        ((StaffCastEnv) (Object) this).addExtension(new ResonantFieldRangeComponent(caster));
    }

    @Inject(
        method = "postExecution(Lat/petrak/hexcasting/api/casting/eval/CastResult;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void hexwright$fireMishapTalismans(CastResult result, CallbackInfo ci) {
        if (result.getResolutionType().getSuccess()) {
            return;
        }
        if ((Object) this instanceof TalismanCastEnv || TalismanCasting.isCasting()) {
            return;
        }
        ServerPlayer caster = ((StaffCastEnv) (Object) this).getCaster();
        if (caster != null) {
            TalismanCasting.onTrigger(caster, TalismanData.Trigger.MISHAP, null, null);
        }
    }

    @Inject(
        method = "postExecution(Lat/petrak/hexcasting/api/casting/eval/CastResult;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void hexwright$playCastAnimation(CastResult result, CallbackInfo ci) {
        if (!result.getResolutionType().getSuccess()) {
            return;
        }
        if ((Object) this instanceof StaffPowerCastEnv
            || (Object) this instanceof TalismanCastEnv
            || (Object) this instanceof PocketCasterCastEnv) {
            return;
        }
        var data = result.getNewData();
        if (data == null
            || !data.getStack().isEmpty()
            || data.getParenCount() != 0
            || result.getContinuation() != SpellContinuation.Done.INSTANCE) {
            return;
        }
        ServerPlayer caster = ((StaffCastEnv) (Object) this).getCaster();
        if (caster != null) {
            StaffCastAnimationTrigger.onSuccessfulCast(caster);
        }
    }
}
