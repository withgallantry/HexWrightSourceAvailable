package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.combat.DamagePolicy;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class TalismanHealing {

    public static final float PER_SECOND_CAP = 4.0F;

    private final DamagePolicy.Window window = new DamagePolicy.Window();

    public static float price(MobEffect effect, int amplifier) {
        if (effect == MobEffects.REGENERATION) {
            int period = 50 >> amplifier;
            return period > 0 ? 20.0F / period : 20.0F;
        }
        if (effect == MobEffects.ABSORPTION) {
            return 4.0F * (amplifier + 1);
        }
        if (effect == MobEffects.HEAL) {
            return 4 << amplifier;
        }
        return 0.0F;
    }

    public @Nullable MobEffectInstance admit(LivingEntity target, MobEffectInstance wanted) {
        MobEffect effect = wanted.getEffect();
        float remaining = this.window.remaining(target.level().getGameTime(), PER_SECOND_CAP);

        for (int amplifier = wanted.getAmplifier(); amplifier >= 0; amplifier--) {
            float price = price(effect, amplifier);
            if (price > remaining) {
                continue;
            }
            this.window.spend(price);
            if (amplifier == wanted.getAmplifier()) {
                return wanted;
            }
            return new MobEffectInstance(effect, wanted.getDuration(), amplifier,
                wanted.isAmbient(), wanted.isVisible(), wanted.showIcon());
        }
        return null;
    }
}
