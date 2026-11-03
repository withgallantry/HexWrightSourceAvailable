package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.malediction.MaledictionFinish;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MaledictionDamage {

    public static final ResourceKey<DamageType> MALEDICTION =
        ResourceKey.create(Registries.DAMAGE_TYPE, Hexwright.id("malediction"));
    public static final ResourceKey<DamageType> SEARING =
        ResourceKey.create(Registries.DAMAGE_TYPE, Hexwright.id("malediction_searing"));
    public static final ResourceKey<DamageType> SUNDERING =
        ResourceKey.create(Registries.DAMAGE_TYPE, Hexwright.id("malediction_sundering"));

    public static final List<ResourceKey<DamageType>> TYPES = List.of(MALEDICTION, SEARING, SUNDERING);

    private MaledictionDamage() {
    }

    public static ResourceKey<DamageType> typeOf(MaledictionFinish finish) {
        return switch (finish) {
            case SEARING -> SEARING;
            case SUNDERING -> SUNDERING;
            default -> MALEDICTION;
        };
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, Entity caster, @Nullable Entity direct) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
            direct != null ? direct : caster, caster);
    }
}
