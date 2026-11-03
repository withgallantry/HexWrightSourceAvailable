package com.bluup.hexwright.server.region;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class RegionBlocks {

    public static final long MAX_SCANNED = 1L << 20;

    public static final int MAX_ITERATIONS = 32768;

    public static final int MAX_BULK_BLOCKS = 1 << 16;

    public static final double MAX_COORDINATE = 3.2e7;

    private RegionBlocks() {
    }

    public static long scanCount(Region region) {
        if (region.isEmpty()) {
            return 0L;
        }
        AABB bounds = region.bounds();
        if (Math.min(Math.min(bounds.minX, bounds.minY), bounds.minZ) < -MAX_COORDINATE
            || Math.max(Math.max(bounds.maxX, bounds.maxY), bounds.maxZ) > MAX_COORDINATE) {
            return Long.MAX_VALUE;
        }
        double spanX = Math.floor(bounds.maxX) - Math.floor(bounds.minX) + 1.0;
        double spanY = Math.floor(bounds.maxY) - Math.floor(bounds.minY) + 1.0;
        double spanZ = Math.floor(bounds.maxZ) - Math.floor(bounds.minZ) + 1.0;
        double count = spanX * spanY * spanZ;
        return count >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) count;
    }

    public static Cursor cursor(Region region) {
        return new Cursor(region, 0L);
    }

    public static Cursor cursorAt(Region region, long index) {
        return new Cursor(region, index);
    }

    public static final class Cursor {

        private final Region region;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final long spanY;
        private final long spanZ;
        private final long total;
        private long index;

        private Cursor(Region region, long index) {
            this.region = region;
            AABB bounds = region.bounds();
            if (region.isEmpty() || scanCount(region) > MAX_SCANNED) {
                this.minX = 0;
                this.minY = 0;
                this.minZ = 0;
                this.spanY = 0L;
                this.spanZ = 0L;
                this.total = 0L;
                this.index = 0L;
                return;
            }
            this.minX = Mth.floor(bounds.minX);
            this.minY = Mth.floor(bounds.minY);
            this.minZ = Mth.floor(bounds.minZ);
            long countX = Mth.floor(bounds.maxX) - this.minX + 1L;
            this.spanY = Mth.floor(bounds.maxY) - this.minY + 1L;
            this.spanZ = Mth.floor(bounds.maxZ) - this.minZ + 1L;
            this.total = countX * this.spanY * this.spanZ;
            this.index = Math.max(0L, index);
        }

        @Nullable
        public BlockPos next() {
            while (index < total) {
                long at = index++;
                int x = minX + (int) (at / (spanY * spanZ));
                long rest = at % (spanY * spanZ);
                int y = minY + (int) (rest / spanZ);
                int z = minZ + (int) (rest % spanZ);
                if (region.contains(x + 0.5, y + 0.5, z + 0.5)) {
                    return new BlockPos(x, y, z);
                }
            }
            return null;
        }

        public long index() {
            return index;
        }
    }
}
