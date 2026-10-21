package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.hexweave.WovenOrbTriggers;
import com.bluup.hexwright.server.talisman.TalismanCasting;
import com.bluup.hexwright.server.talisman.TalismanData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerStruckWoundMixin {

    @Unique
    private float hexwright$vitalityBeforeHurt;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void hexwright$recordVitality(DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        this.hexwright$vitalityBeforeHurt = self.getHealth() + self.getAbsorptionAmount();
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void hexwright$parryWithWovenOrb(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (cir.getReturnValueZ() && !self.isDeadOrDying()) {
            WovenOrbTriggers.onStruck(self, source);
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void hexwright$fireStruckAndWoundTalismans(DamageSource source, float amount,
                                                       CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!cir.getReturnValueZ() || self.isDeadOrDying() || TalismanCasting.isCasting()) {
            return;
        }
        float lost = this.hexwright$vitalityBeforeHurt - (self.getHealth() + self.getAbsorptionAmount());
        TalismanCasting.onTrigger(self, TalismanData.Trigger.STRUCK, source.getEntity(), (double) amount);
        if (lost > 0.0f) {
            TalismanCasting.onTrigger(self, TalismanData.Trigger.WOUND, source.getEntity(), (double) lost);
        }
    }
}
