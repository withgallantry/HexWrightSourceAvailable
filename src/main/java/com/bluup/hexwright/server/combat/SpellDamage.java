package com.bluup.hexwright.server.combat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;

public final class SpellDamage {

    public static final float PER_HIT_CAP = 15.0F;

    public static final float PER_SECOND_CAP = 20.0F;

    private static final List<ResourceKey<DamageType>> SPELL_TYPES =
        List.of(DamageTypes.MAGIC, DamageTypes.INDIRECT_MAGIC, DamageTypes.GENERIC, Reprisal.TYPE);

    private final DamagePolicy.Window window = new DamagePolicy.Window();

    public float limit(LivingEntity victim, DamageSource source, float amount) {
        float allowed = DamagePolicy.normaliseProjectile(source, amount);
        if (!isSpellShaped(source)) {
            return allowed;
        }
        allowed = Math.min(allowed, PER_HIT_CAP);
        return Math.min(allowed, this.window.remaining(victim.level().getGameTime(), PER_SECOND_CAP));
    }

    public void spend(DamageSource source, float vitalityLost) {
        if (isSpellShaped(source)) {
            this.window.spend(vitalityLost);
        }
    }

    public static boolean isSpellShaped(DamageSource source) {
        for (ResourceKey<DamageType> type : SPELL_TYPES) {
            if (source.is(type)) {
                return true;
            }
        }
        return false;
    }
}
