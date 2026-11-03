package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.talisman.TalismanCasting;
import com.bluup.hexwright.server.talisman.TalismanData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtTalismanMixin {

    @Unique
    private float hexwright$vitalityBeforeHurt;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void hexwright$recordVitality(DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.hexwright$vitalityBeforeHurt = self.getHealth() + self.getAbsorptionAmount();
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void hexwright$fireHurtTalismans(DamageSource source, float amount,
                                             CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!cir.getReturnValueZ() || !(source.getEntity() instanceof ServerPlayer dealer)
            || dealer == self || TalismanCasting.isCasting()) {
            return;
        }
        float dealt = this.hexwright$vitalityBeforeHurt - (self.getHealth() + self.getAbsorptionAmount());
        if (dealt <= 0.0f) {
            return;
        }
        TalismanCasting.onTrigger(dealer, TalismanData.Trigger.HURT, self, (double) dealt);
    }
}
