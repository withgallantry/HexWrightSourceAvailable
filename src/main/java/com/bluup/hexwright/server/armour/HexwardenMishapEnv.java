package com.bluup.hexwright.server.armour;

import at.petrak.hexcasting.api.casting.eval.MishapEnvironment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class HexwardenMishapEnv extends MishapEnvironment {

    private final MishapEnvironment delegate;
    private final float protection;

    public HexwardenMishapEnv(MishapEnvironment delegate, ServerPlayer caster, ArmourTier tier) {
        super(caster.serverLevel(), caster);
        this.delegate = delegate;
        this.protection = protection(tier);
    }

    public static float protection(ArmourTier tier) {
        return switch (tier) {
            case IRON -> 0.25f;
            case GOLDEN -> 0.5f;
            case DIAMOND -> 0.75f;
            case NETHERITE -> 1.0f;
        };
    }

    @Override
    public void yeetHeldItemsTowards(Vec3 targetPos) {
        if (protection < 1.0f) {
            delegate.yeetHeldItemsTowards(targetPos);
        }
    }

    @Override
    public void dropHeldItems() {
        if (protection < 1.0f) {
            delegate.dropHeldItems();
        }
    }

    @Override
    public void drown() {
        if (protection < 1.0f) {
            delegate.drown();
        }
    }

    @Override
    public void damage(float healthProportion) {
        delegate.damage(healthProportion * (1.0f - protection));
    }

    @Override
    public void removeXp(int amount) {
        delegate.removeXp(Math.round(amount * (1.0f - protection)));
    }

    @Override
    public void blind(int ticks) {
        delegate.blind(Math.round(ticks * (1.0f - protection)));
    }
}
