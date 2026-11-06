package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import com.bluup.hexwright.server.boss.arbiter.ArbiterEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StaffCastEnv.class)
public abstract class StaffCastEnvArbiterMixin {

    @Inject(method = "handleNewPatternOnServer", at = @At("HEAD"), remap = false)
    private static void hexwright$answerArbiter(ServerPlayer sender, MsgNewSpellPatternC2S msg, CallbackInfo ci) {
        ArbiterEntity.drawn(sender, msg.pattern());
    }
}
