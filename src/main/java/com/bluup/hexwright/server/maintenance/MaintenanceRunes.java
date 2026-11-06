package com.bluup.hexwright.server.maintenance;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class MaintenanceRunes {

    public static final int FACETS = 6;
    public static final int ROWS = 2;
    public static final int SLOTS = FACETS * ROWS;

    private static final int[] GLYPHS = {9, 6, 15, 0, 3, 13, 7, 11, 4, 5, 14, 2};

    public static final int[] ORDER = {6, 15, 7, 13, 4, 2};

    public static final float HALF_LENGTH = 0.95F;
    public static final float RADIUS = 0.42F;
    public static final float BODY = 0.62F;
    public static final float ROW_OFFSET = 0.3F;
    public static final float RUNE_SIZE = 0.3F;

    private MaintenanceRunes() {
    }

    public static Vec3 centre(BlockPos lower) {
        return Vec3.atBottomCenterOf(lower).add(0.0, 1.0, 0.0);
    }

    public static int glyph(int slot) {
        return GLYPHS[slot];
    }

    public static int expected(int step) {
        int glyph = ORDER[step];
        for (int slot = 0; slot < SLOTS; slot++) {
            if (GLYPHS[slot] == glyph) {
                return slot;
            }
        }
        throw new IllegalStateException("Rune " + glyph + " is in the order but not on the crystal");
    }

    public static double facetAngle(int facet) {
        return (facet + 0.5) * Math.PI / 3.0;
    }

    public static double apothem() {
        return RADIUS * Math.cos(Math.PI / 6.0);
    }

    public static float rowHeight(int row) {
        return row == 0 ? -ROW_OFFSET : ROW_OFFSET;
    }

    public static int slotAt(BlockPos lower, Vec3 eye, Vec3 through) {
        Vec3 direction = through.subtract(eye);
        if (direction.lengthSqr() < 1.0E-8) {
            return -1;
        }
        direction = direction.normalize();
        Vec3 from = eye.subtract(centre(lower));
        double apothem = apothem();
        double nearest = Double.MAX_VALUE;
        int hit = -1;
        for (int facet = 0; facet < FACETS; facet++) {
            double angle = facetAngle(facet);
            double nx = Math.cos(angle);
            double nz = Math.sin(angle);
            double approach = nx * direction.x + nz * direction.z;
            if (approach >= 0.0) {
                continue;
            }
            double distance = (apothem - (nx * from.x + nz * from.z)) / approach;
            if (distance < 0.0 || distance >= nearest) {
                continue;
            }
            Vec3 at = from.add(direction.scale(distance));
            double across = -nz * at.x + nx * at.z;
            if (Math.abs(at.y) > BODY || Math.abs(across) > RADIUS / 2.0) {
                continue;
            }
            nearest = distance;
            hit = facet * ROWS + (at.y >= 0.0 ? 1 : 0);
        }
        return hit;
    }
}
