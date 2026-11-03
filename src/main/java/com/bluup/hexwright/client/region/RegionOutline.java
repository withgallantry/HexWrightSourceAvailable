package com.bluup.hexwright.client.region;

import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionBlocks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.BitSet;

record RegionOutline(int originX, int originY, int originZ, int[] segments, AABB box) {

    int lineCount() {
        return segments.length / 6;
    }

    static RegionOutline trace(Region region) {
        AABB bounds = region.bounds();
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int[] size = {
            Mth.floor(bounds.maxX) - minX + 1,
            Mth.floor(bounds.maxY) - minY + 1,
            Mth.floor(bounds.maxZ) - minZ + 1
        };

        int strideY = size[2] + 2;
        int strideX = (size[1] + 2) * strideY;
        int[] stride = {strideX, strideY, 1};
        BitSet held = new BitSet((size[0] + 2) * strideX);
        RegionBlocks.Cursor cursor = RegionBlocks.cursor(region);
        for (BlockPos pos = cursor.next(); pos != null; pos = cursor.next()) {
            held.set((pos.getX() - minX + 1) * strideX + (pos.getY() - minY + 1) * strideY + (pos.getZ() - minZ + 1));
        }

        IntArrayList segments = new IntArrayList();
        for (int axis = 0; axis < 3; axis++) {
            traceAxis(held, size, stride, axis, segments);
        }
        AABB box = new AABB(minX, minY, minZ, minX + size[0], minY + size[1], minZ + size[2]);
        return new RegionOutline(minX, minY, minZ, segments.toIntArray(), box);
    }

    private static void traceAxis(BitSet held, int[] size, int[] stride, int u, IntArrayList out) {
        int v = (u + 1) % 3;
        int w = (u + 2) % 3;
        int[] end = new int[3];
        for (int b = 1; b <= size[v] + 1; b++) {
            for (int c = 1; c <= size[w] + 1; c++) {
                int runStart = -1;
                for (int a = 1; a <= size[u] + 1; a++) {
                    boolean crease = a <= size[u]
                        && isCrease(held, a * stride[u] + b * stride[v] + c * stride[w], stride[v], stride[w]);
                    if (crease && runStart < 0) {
                        runStart = a;
                    } else if (!crease && runStart >= 0) {
                        end[v] = b - 1;
                        end[w] = c - 1;
                        end[u] = runStart - 1;
                        out.add(end[0]);
                        out.add(end[1]);
                        out.add(end[2]);
                        end[u] = a - 1;
                        out.add(end[0]);
                        out.add(end[1]);
                        out.add(end[2]);
                        runStart = -1;
                    }
                }
            }
        }
    }

    private static boolean isCrease(BitSet held, int index, int strideV, int strideW) {
        boolean p = held.get(index - strideV - strideW);
        boolean q = held.get(index - strideW);
        boolean r = held.get(index - strideV);
        boolean s = held.get(index);
        int count = (p ? 1 : 0) + (q ? 1 : 0) + (r ? 1 : 0) + (s ? 1 : 0);
        return count == 1 || count == 3 || (count == 2 && p == s);
    }
}
