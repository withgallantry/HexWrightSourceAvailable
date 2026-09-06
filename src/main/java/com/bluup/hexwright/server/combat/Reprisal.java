package com.bluup.hexwright.server.combat;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

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
        LivingEntity caster = casterOf(source, level);
        if (caster == null || caster == wearer || caster.level() != level) {
            return;
        }
        caster.hurt(new DamageSource(
            level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),
            null, wearer), attempted * FRACTION);
    }

    private static @Nullable LivingEntity casterOf(DamageSource source, ServerLevel level) {
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living) {
            return living;
        }
        return attacker == null ? SpellCaster.current(level.getGameTime()) : null;
    }
}
