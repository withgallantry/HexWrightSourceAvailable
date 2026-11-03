package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AreaCastRangeComponent implements CastingEnvironmentComponent.IsVecInRange {

    private static final Key<AreaCastRangeComponent> KEY = new Key<>() {
    };

    private final AABB scanBox;

    public AreaCastRangeComponent(AABB scanBox) {
        this.scanBox = scanBox;
    }

    @Override
    public Key<?> getKey() {
        return KEY;
    }

    @Override
    public boolean onIsVecInRange(Vec3 vec, boolean current) {
        return current || scanBox.contains(vec);
    }
}
