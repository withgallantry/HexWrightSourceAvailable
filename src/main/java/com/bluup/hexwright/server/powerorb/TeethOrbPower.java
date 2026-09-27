package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.portal.VoidTearManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TeethOrbPower {

    public static final long TEAR_COST = MediaConstants.DUST_UNIT / 4;

    private static final double SIGHT_TOLERANCE_SQ = 1.0;

    private TeethOrbPower() {
    }

    public static void cast(ServerLevel level, LivingEntity caster, Vec3 at) {
        VoidTearManager.get(level).open(level, caster, at);
    }

    public static boolean atLimit(ServerLevel level, LivingEntity caster) {
        return VoidTearManager.get(level).countFor(caster.getUUID()) >= VoidTearManager.MAX_TEARS_PER_CASTER;
    }

    public static boolean hasLineOfSight(ServerLevel level, LivingEntity caster, Vec3 at) {
        Vec3 eye = caster.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(
            eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS
            || hit.getLocation().distanceToSqr(at) <= SIGHT_TOLERANCE_SQ;
    }
}
