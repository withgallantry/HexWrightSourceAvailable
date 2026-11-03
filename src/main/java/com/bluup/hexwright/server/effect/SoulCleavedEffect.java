package com.bluup.hexwright.server.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class SoulCleavedEffect extends MobEffect {

    private static final int WISP_INTERVAL_TICKS = 4;

    SoulCleavedEffect() {
        super(MobEffectCategory.HARMFUL, 0x5CE8E4);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % WISP_INTERVAL_TICKS == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D,
            entity.getZ(), 1, entity.getBbWidth() * 0.3D, entity.getBbHeight() * 0.2D, entity.getBbWidth() * 0.3D,
            0.02D);
    }
}
