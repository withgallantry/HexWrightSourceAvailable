package com.bluup.hexwright.server.region;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RegionAmbit {

    private RegionAmbit() {
    }

    public static void assertIterable(Region region, Iota iota, int reverseIdx) {
        if (region.isEmpty()) {
            return;
        }
        assertScannable(region, iota, reverseIdx);

        int found = 0;
        RegionBlocks.Cursor cursor = RegionBlocks.cursor(region);
        while (cursor.next() != null) {
            if (++found > RegionBlocks.MAX_ITERATIONS) {
                throw MishapInvalidIota.of(iota, reverseIdx,
                    "hexwright.region_too_many_blocks", RegionBlocks.MAX_ITERATIONS);
            }
        }
    }

    public static void assertVolumeReachable(CastingEnvironment env, Region region, Iota iota, int reverseIdx) {
        if (region.isEmpty()) {
            return;
        }
        assertScannable(region, iota, reverseIdx);

        boolean sampled = false;
        RegionBlocks.Cursor cursor = RegionBlocks.cursor(region);
        BlockPos pos;
        while ((pos = cursor.next()) != null) {
            sampled = true;
            env.assertPosInRange(pos);
        }
        if (!sampled) {
            assertCornersReachable(env, region.bounds());
        }
    }

    public static void assertScannable(Region region, Iota iota, int reverseIdx) {
        if (RegionBlocks.scanCount(region) > RegionBlocks.MAX_SCANNED) {
            throw MishapInvalidIota.of(iota, reverseIdx,
                "hexwright.region_too_large", RegionBlocks.MAX_SCANNED);
        }
    }

    public static boolean isWithinAmbit(CastingEnvironment env, Region region) {
        if (region.isEmpty()) {
            return true;
        }

        boolean sampled = false;
        RegionBlocks.Cursor cursor = RegionBlocks.cursor(region);
        BlockPos pos;
        while ((pos = cursor.next()) != null) {
            sampled = true;
            if (!env.isVecInAmbit(Vec3.atCenterOf(pos))) {
                return false;
            }
        }
        if (sampled) {
            return true;
        }
        for (Vec3 corner : corners(region.bounds())) {
            if (!env.isVecInAmbit(corner)) {
                return false;
            }
        }
        return true;
    }

    private static void assertCornersReachable(CastingEnvironment env, AABB bounds) {
        for (Vec3 corner : corners(bounds)) {
            env.assertVecInRange(corner);
        }
    }

    private static Vec3[] corners(AABB bounds) {
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vec3(
                (i & 1) == 0 ? bounds.minX : bounds.maxX,
                (i & 2) == 0 ? bounds.minY : bounds.maxY,
                (i & 4) == 0 ? bounds.minZ : bounds.maxZ);
        }
        return corners;
    }
}
