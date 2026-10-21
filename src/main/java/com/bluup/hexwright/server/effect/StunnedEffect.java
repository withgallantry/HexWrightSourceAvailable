package com.bluup.hexwright.server.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class StunnedEffect extends MobEffect {

    private static final int RING_POINTS = 22;

    private static final double RING_RADIUS = 0.5D;

    private static final double RING_LIFT = 0.5D;

    StunnedEffect() {
        super(MobEffectCategory.HARMFUL, 0xA77BFF);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        double angle = entity.tickCount * (Math.PI * 2.0D) / RING_POINTS;
        level.sendParticles(ParticleTypes.CRIT,
            entity.getX() + Math.cos(angle) * RING_RADIUS,
            entity.getY() + entity.getBbHeight() + RING_LIFT,
            entity.getZ() + Math.sin(angle) * RING_RADIUS,
            1, 0.0D, 0.0D, 0.0D, 0.0D);
    }
}
