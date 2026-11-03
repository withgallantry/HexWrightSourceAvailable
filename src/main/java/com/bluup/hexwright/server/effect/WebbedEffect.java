package com.bluup.hexwright.server.effect;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class WebbedEffect extends MobEffect {

    private static final Vec3 STUCK = new Vec3(0.25D, 0.05D, 0.25D);

    private static final int STRAND_INTERVAL = 5;

    WebbedEffect() {
        super(MobEffectCategory.HARMFUL, 0xDCDCE6);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        entity.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), STUCK);
        if (entity.level() instanceof ServerLevel level && entity.tickCount % STRAND_INTERVAL == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()),
                entity.getX(), entity.getY(0.5D), entity.getZ(), 2, entity.getBbWidth() * 0.35D,
                entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.35D, 0.0D);
        }
    }
}
