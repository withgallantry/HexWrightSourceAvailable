package com.bluup.hexwright.server.block;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ResonantFieldRangeComponent implements CastingEnvironmentComponent.IsVecInRange {

    private static final Key<ResonantFieldRangeComponent> KEY = new Key<>() {
    };

    private final ServerPlayer caster;

    public ResonantFieldRangeComponent(ServerPlayer caster) {
        this.caster = caster;
    }

    @Override
    public Key<?> getKey() {
        return KEY;
    }

    @Override
    public boolean onIsVecInRange(Vec3 vec, boolean current) {
        if (current) {
            return true;
        }
        ServerLevel level = caster.serverLevel();
        Vec3 casterPos = caster.position();
        for (ResonantFieldRegistry.Field field : ResonantFieldRegistry.get(level.getServer()).fieldsIn(level.dimension())) {
            if (field.containsBoth(casterPos, vec)) {
                return true;
            }
        }
        return false;
    }
}
