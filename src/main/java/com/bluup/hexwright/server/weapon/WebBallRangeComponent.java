package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class WebBallRangeComponent implements CastingEnvironmentComponent.IsVecInRange {

    private static final Key<WebBallRangeComponent> KEY = new Key<>() {
    };

    private final ServerPlayer caster;

    public WebBallRangeComponent(ServerPlayer caster) {
        this.caster = caster;
    }

    @Override
    public Key<?> getKey() {
        return KEY;
    }

    @Override
    public boolean onIsVecInRange(Vec3 vec, boolean current) {
        return current || WebBallItem.holds(this.caster, this.caster.serverLevel(), vec);
    }
}
