package com.bluup.hexwright.server.wardingbox;

import at.petrak.hexcasting.api.casting.eval.MishapEnvironment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class WardingBoxMishapEnv extends MishapEnvironment {

    protected WardingBoxMishapEnv(ServerLevel world) {
        super(world, null);
    }

    @Override
    public void yeetHeldItemsTowards(Vec3 targetPos) {
    }

    @Override
    public void dropHeldItems() {
    }

    @Override
    public void drown() {
    }

    @Override
    public void damage(float healthProportion) {
    }

    @Override
    public void removeXp(int amount) {
    }

    @Override
    public void blind(int ticks) {
    }
}
