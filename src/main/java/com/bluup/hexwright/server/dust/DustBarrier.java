package com.bluup.hexwright.server.dust;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class DustBarrier {

    private static final int SAMPLES = 5;

    private DustBarrier() {
    }

    public static boolean dams(Level level, BlockPos from, BlockPos to) {
        if (DustTuning.damsFluid == 0) {
            return false;
        }
        List<DustSupport.Surface> found = DustSupport.surfaces(level);
        if (found.isEmpty()) {
            return false;
        }
        double fx = from.getX() + 0.5;
        double fy = from.getY() + 0.5;
        double fz = from.getZ() + 0.5;
        double tx = to.getX() + 0.5;
        double ty = to.getY() + 0.5;
        double tz = to.getZ() + 0.5;
        AABB span = new AABB(fx, fy, fz, tx, ty, tz);
        for (int i = 0; i < found.size(); i++) {
            DustSupport.Surface surface = found.get(i);
            double hold = surface.hold();
            if (surface.level != level || hold <= 0.0 || !surface.touches(span)) {
                continue;
            }
            if (!crosses(surface, fx, fy, fz, tx, ty, tz)) {
                continue;
            }
            if (hold >= DustSupport.FIRM || level.getRandom().nextDouble() < hold) {
                return true;
            }
        }
        return false;
    }

    public static void press(Level level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        if (state.isEmpty()) {
            return;
        }
        Fluid fluid = state.getType();
        BlockPos at = pos.immutable();
        if (level.getFluidTicks().hasScheduledTick(at, fluid)) {
            return;
        }
        level.scheduleTick(at, fluid, fluid.getTickDelay(level));
    }

    private static boolean crosses(DustSupport.Surface surface, double fx, double fy, double fz,
                                   double tx, double ty, double tz) {
        for (int s = 0; s < SAMPLES; s++) {
            double t = (s + 0.5) / SAMPLES;
            if (surface.distance(fx + (tx - fx) * t, fy + (ty - fy) * t, fz + (tz - fz) * t) <= 0.0) {
                return true;
            }
        }
        return false;
    }
}
