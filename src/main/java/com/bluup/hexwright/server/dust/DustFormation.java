package com.bluup.hexwright.server.dust;

import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionDistance;
import net.minecraft.world.phys.AABB;

public final class DustFormation {

    public final Region region;
    public final AABB bounds;
    public final double centerX;
    public final double centerY;
    public final double centerZ;
    public final double fillVolume;
    public final double shellVolume;

    public DustFormation(Region region) {
        this.region = region;
        this.bounds = region.bounds();
        this.centerX = (bounds.minX + bounds.maxX) * 0.5;
        this.centerY = (bounds.minY + bounds.maxY) * 0.5;
        this.centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
        RegionDistance.Measure measure = RegionDistance.measure(region);
        double fill = measure.volume() + measure.area() * DustTuning.minThickness * 0.5;
        fill = Math.min(fill, measure.area() * DustTuning.crustThickness);
        this.fillVolume = Math.max(fill, DustTuning.minVolume);
        this.shellVolume = Math.max(Math.min(measure.area() * DustTuning.shellThickness, fillVolume),
            DustTuning.minVolume);
    }

    public double volume(boolean shell) {
        return shell ? shellVolume : fillVolume;
    }

    public double distance(double x, double y, double z) {
        return RegionDistance.distance(region, x, y, z);
    }

    public static double surfaceDistance(double regionDistance, boolean shell) {
        if (shell) {
            double half = DustTuning.shellThickness * 0.5;
            return Math.abs(regionDistance - (DustTuning.surfaceProud - half)) - half;
        }
        return regionDistance - DustTuning.surfaceProud;
    }

    public static double surfaceReach() {
        return DustTuning.surfaceProud;
    }
}
