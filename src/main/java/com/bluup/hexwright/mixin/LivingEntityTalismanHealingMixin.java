package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.talisman.TalismanCasting;
import com.bluup.hexwright.server.talisman.TalismanHealing;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTalismanHealingMixin {

    @Unique
    @Nullable
    private TalismanHealing hexwright$talismanHealing;

    @Unique
    private boolean hexwright$readmittingTalismanEffect;

    @Inject(
        method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hexwright$capTalismanHealing(MobEffectInstance instance, @Nullable Entity source,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (this.hexwright$readmittingTalismanEffect || !TalismanCasting.isCasting()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (TalismanHealing.price(instance.getEffect(), instance.getAmplifier()) <= 0.0F) {
            return;
        }
        if (this.hexwright$talismanHealing == null) {
            this.hexwright$talismanHealing = new TalismanHealing();
        }

        MobEffectInstance allowed = this.hexwright$talismanHealing.admit(self, instance);
        if (allowed == instance) {
            return;
        }
        if (allowed == null) {
            cir.setReturnValue(false);
            return;
        }

        this.hexwright$readmittingTalismanEffect = true;
        try {
            cir.setReturnValue(self.addEffect(allowed, source));
        } finally {
            this.hexwright$readmittingTalismanEffect = false;
        }
    }
}
