package com.bluup.hexwright.server.combat;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

public final class Reprisal {

    public static final float FRACTION = 0.5F;

    public static final ResourceKey<DamageType> TYPE =
        ResourceKey.create(Registries.DAMAGE_TYPE, Hexwright.id("reprisal"));

    private Reprisal() {
    }

    public static void answer(LivingEntity wearer, DamageSource source, float attempted) {
        if (source.is(TYPE) || attempted <= 0.0F
            || !(wearer.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity caster = SpellCaster.attackerOf(source, level);
        if (caster == null || caster == wearer || caster.level() != level) {
            return;
        }
        caster.hurt(new DamageSource(
            level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),
            null, wearer), attempted * FRACTION);
    }
}
