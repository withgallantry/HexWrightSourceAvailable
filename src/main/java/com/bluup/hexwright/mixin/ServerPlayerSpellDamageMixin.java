package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.combat.Reprisal;
import com.bluup.hexwright.server.combat.RingOfNegationItem;
import com.bluup.hexwright.server.combat.SpellDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSpellDamageMixin {

    @Unique
    private final SpellDamage hexwright$spellDamage = new SpellDamage();

    @Unique
    private float hexwright$vitalityBeforeSpell;

    @Unique
    private float hexwright$attempted;

    @Unique
    @Nullable
    private RingOfNegationItem.Kind hexwright$ring;

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float hexwright$capSpellDamage(float amount, DamageSource source, float original) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        this.hexwright$vitalityBeforeSpell = self.getHealth() + self.getAbsorptionAmount();
        this.hexwright$attempted = amount;
        this.hexwright$ring = RingOfNegationItem.wornBy(self);
        if (this.hexwright$ring == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }
        return this.hexwright$spellDamage.limit(self, source, amount);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void hexwright$chargeSpellBudget(DamageSource source, float amount,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (this.hexwright$ring == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (!cir.getReturnValueZ()) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        float lost = this.hexwright$vitalityBeforeSpell - (self.getHealth() + self.getAbsorptionAmount());
        if (lost > 0.0F) {
            this.hexwright$spellDamage.spend(source, lost);
        }
        if (this.hexwright$ring == RingOfNegationItem.Kind.REPRISAL
            && SpellDamage.isSpellShaped(source)) {
            Reprisal.answer(self, source, this.hexwright$attempted);
        }
    }
}
