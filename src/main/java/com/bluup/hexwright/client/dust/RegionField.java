package com.bluup.hexwright.client.dust;

import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionDistance;
import net.minecraft.world.phys.AABB;

import java.util.SplittableRandom;

final class RegionField {

    private static final int MAX_NODES = 40_000;
    private static final int MAX_AXIS = 64;
    private static final int MAX_PARTS = 16;
    private static final float MARGIN = 1.5f;
    private static final int PROBES = 12;
    private static final float SURFACE_CORRECTION = 1.18f;

    final Region region;
    final boolean shell;
    final double centerX;
    final double centerY;
    final double centerZ;
    final float halfX;
    final float halfY;
    final float halfZ;
    final Grid grid;

    private RegionField(Region region, boolean shell, AABB bounds, Grid grid) {
        this.region = region;
        this.shell = shell;
        this.centerX = (bounds.minX + bounds.maxX) * 0.5;
        this.centerY = (bounds.minY + bounds.maxY) * 0.5;
        this.centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
        this.halfX = (float) (bounds.getXsize() * 0.5);
        this.halfY = (float) (bounds.getYsize() * 0.5);
        this.halfZ = (float) (bounds.getZsize() * 0.5);
        this.grid = grid;
    }

    static RegionField build(Region region, boolean shell, RegionField previous) {
        AABB bounds = region.bounds();
        if (previous != null && sameShapeMoved(region, bounds, previous)) {
            return new RegionField(region, shell, bounds, previous.grid);
        }
        return new RegionField(region, shell, bounds, Grid.bake(region, bounds));
    }

    float sample(float px, float py, float pz, float[] normal) {
        Grid g = grid;
        float fx = (px - g.minX) / g.cellX;
        float fy = (py - g.minY) / g.cellY;
        float fz = (pz - g.minZ) / g.cellZ;
        float cx = clamp(fx, 0.0f, g.nx - 1.001f);
        float cy = clamp(fy, 0.0f, g.ny - 1.001f);
        float cz = clamp(fz, 0.0f, g.nz - 1.001f);
        int ix = (int) cx;
        int iy = (int) cy;
        int iz = (int) cz;
        float tx = cx - ix;
        float ty = cy - iy;
        float tz = cz - iz;
        float[] d = g.dist;
        int nyz = g.ny * g.nz;
        int i000 = ix * nyz + iy * g.nz + iz;
        int i001 = i000 + 1;
        int i010 = i000 + g.nz;
        int i011 = i010 + 1;
        int i100 = i000 + nyz;
        int i101 = i100 + 1;
        int i110 = i100 + g.nz;
        int i111 = i110 + 1;

        float d00 = d[i000] + (d[i001] - d[i000]) * tz;
        float d01 = d[i010] + (d[i011] - d[i010]) * tz;
        float d10 = d[i100] + (d[i101] - d[i100]) * tz;
        float d11 = d[i110] + (d[i111] - d[i110]) * tz;
        float d0 = d00 + (d01 - d00) * ty;
        float d1 = d10 + (d11 - d10) * ty;
        float value = d0 + (d1 - d0) * tx;

        float gx = (d1 - d0) / g.cellX;
        float gy = ((d01 - d00) + ((d11 - d10) - (d01 - d00)) * tx) / g.cellY;
        float e0 = (d[i001] - d[i000]) + ((d[i011] - d[i010]) - (d[i001] - d[i000])) * ty;
        float e1 = (d[i101] - d[i100]) + ((d[i111] - d[i110]) - (d[i101] - d[i100])) * ty;
        float gz = (e0 + (e1 - e0) * tx) / g.cellZ;

        float ox = (fx - cx) * g.cellX;
        float oy = (fy - cy) * g.cellY;
        float oz = (fz - cz) * g.cellZ;
        float outsideSq = ox * ox + oy * oy + oz * oz;
        if (outsideSq > 1.0e-6f) {
            float outside = (float) Math.sqrt(outsideSq);
            value += outside;
            gx = ox;
            gy = oy;
            gz = oz;
        }
        float length = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (length > 1.0e-6f) {
            normal[0] = gx / length;
            normal[1] = gy / length;
            normal[2] = gz / length;
        } else {
            normal[0] = 0.0f;
            normal[1] = 0.0f;
            normal[2] = 0.0f;
        }
        return value;
    }

    boolean surfacePoint(float u, float[] out, float[] normal) {
        int[] nodes = grid.surfaceNodes();
        if (nodes.length == 0) {
            return false;
        }
        Grid g = grid;
        int i = nodes[Math.min((int) (u * nodes.length), nodes.length - 1)];
        int nyz = g.ny * g.nz;
        float x = g.minX + (i / nyz) * g.cellX;
        float y = g.minY + ((i / g.nz) % g.ny) * g.cellY;
        float z = g.minZ + (i % g.nz) * g.cellZ;
        for (int step = 0; step < 2; step++) {
            float d = sample(x, y, z, normal);
            x -= normal[0] * d;
            y -= normal[1] * d;
            z -= normal[2] * d;
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        return true;
    }

    int partAt(float px, float py, float pz) {
        Grid g = grid;
        int ix = Math.round(clamp((px - g.minX) / g.cellX, 0.0f, g.nx - 1));
        int iy = Math.round(clamp((py - g.minY) / g.cellY, 0.0f, g.ny - 1));
        int iz = Math.round(clamp((pz - g.minZ) / g.cellZ, 0.0f, g.nz - 1));
        return g.part[(ix * g.ny + iy) * g.nz + iz];
    }

    int partFor(float u) {
        float[] cumulative = grid.partCumulative;
        for (int i = 0; i < grid.parts - 1; i++) {
            if (u < cumulative[i]) {
                return i;
            }
        }
        return grid.parts - 1;
    }

    private static boolean sameShapeMoved(Region region, AABB bounds, RegionField previous) {
        if (Math.abs(bounds.getXsize() * 0.5 - previous.halfX) > 1.0e-4
            || Math.abs(bounds.getYsize() * 0.5 - previous.halfY) > 1.0e-4
            || Math.abs(bounds.getZsize() * 0.5 - previous.halfZ) > 1.0e-4) {
            return false;
        }
        double cx = (bounds.minX + bounds.maxX) * 0.5;
        double cy = (bounds.minY + bounds.maxY) * 0.5;
        double cz = (bounds.minZ + bounds.maxZ) * 0.5;
        SplittableRandom random = new SplittableRandom(0xD057L);
        for (int i = 0; i < PROBES; i++) {
            double qx = (random.nextDouble() * 2.0 - 1.0) * (previous.halfX + 1.0);
            double qy = (random.nextDouble() * 2.0 - 1.0) * (previous.halfY + 1.0);
            double qz = (random.nextDouble() * 2.0 - 1.0) * (previous.halfZ + 1.0);
            double now = RegionDistance.distance(region, cx + qx, cy + qy, cz + qz);
            double before = RegionDistance.distance(previous.region,
                previous.centerX + qx, previous.centerY + qy, previous.centerZ + qz);
            if (Math.abs(now - before) > 1.0e-3) {
                return false;
            }
        }
        return true;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    static final class Grid {
        float minX;
        float minY;
        float minZ;
        float cellX;
        float cellY;
        float cellZ;
        int nx;
        int ny;
        int nz;
        float[] dist;
        byte[] part;
        int parts;
        float[] partX;
        float[] partY;
        float[] partZ;
        byte[] partAxis;
        float[] partHalfAxis;
        float[] partRadial;
        float[] partDepth;
        float[] partCumulative;
        float surfaceArea;
        private int[] surfaceNodes;

        int[] surfaceNodes() {
            int[] nodes = surfaceNodes;
            if (nodes == null) {
                float near = 0.75f * Math.max(cellX, Math.max(cellY, cellZ));
                int count = 0;
                for (float d : dist) {
                    if (Math.abs(d) <= near) {
                        count++;
                    }
                }
                nodes = new int[count];
                int n = 0;
                for (int i = 0; i < dist.length; i++) {
                    if (Math.abs(dist[i]) <= near) {
                        nodes[n++] = i;
                    }
                }
                surfaceNodes = nodes;
            }
            return nodes;
        }

        static Grid bake(Region region, AABB bounds) {
            Grid g = new Grid();
            double ex = bounds.getXsize() + 2.0 * MARGIN;
            double ey = bounds.getYsize() + 2.0 * MARGIN;
            double ez = bounds.getZsize() + 2.0 * MARGIN;
            double cell = Math.max(0.2, Math.cbrt(ex * ey * ez / MAX_NODES));
            while (true) {
                g.nx = axis(ex, cell);
                g.ny = axis(ey, cell);
                g.nz = axis(ez, cell);
                if ((long) g.nx * g.ny * g.nz <= MAX_NODES) {
                    break;
                }
                cell *= 1.1;
            }
            g.cellX = (float) (ex / (g.nx - 1));
            g.cellY = (float) (ey / (g.ny - 1));
            g.cellZ = (float) (ez / (g.nz - 1));
            g.minX = (float) -(ex * 0.5);
            g.minY = (float) -(ey * 0.5);
            g.minZ = (float) -(ez * 0.5);
            double cx = (bounds.minX + bounds.maxX) * 0.5;
            double cy = (bounds.minY + bounds.maxY) * 0.5;
            double cz = (bounds.minZ + bounds.maxZ) * 0.5;

            int total = g.nx * g.ny * g.nz;
            g.dist = new float[total];
            for (int ix = 0; ix < g.nx; ix++) {
                double x = cx + g.minX + ix * g.cellX;
                for (int iy = 0; iy < g.ny; iy++) {
                    double y = cy + g.minY + iy * g.cellY;
                    for (int iz = 0; iz < g.nz; iz++) {
                        double z = cz + g.minZ + iz * g.cellZ;
                        g.dist[(ix * g.ny + iy) * g.nz + iz] =
                            (float) RegionDistance.distance(region, x, y, z);
                    }
                }
            }
            g.labelParts();
            return g;
        }

        private static int axis(double extent, double cell) {
            return Math.max(4, Math.min(MAX_AXIS, (int) Math.ceil(extent / cell) + 1));
        }

        private void labelParts() {
            int total = dist.length;
            float inside = 0.5f * Math.min(cellX, Math.min(cellY, cellZ));
            part = new byte[total];
            java.util.Arrays.fill(part, (byte) -1);
            int[] queue = new int[total];
            int nyz = ny * nz;
            int labels = 0;

            float[] sumX = new float[MAX_PARTS];
            float[] sumY = new float[MAX_PARTS];
            float[] sumZ = new float[MAX_PARTS];
            int[] count = new int[MAX_PARTS];
            float[] low = new float[MAX_PARTS * 3];
            float[] high = new float[MAX_PARTS * 3];
            float[] depth = new float[MAX_PARTS];
            float[] weight = new float[MAX_PARTS];
            java.util.Arrays.fill(low, Float.MAX_VALUE);
            java.util.Arrays.fill(high, -Float.MAX_VALUE);

            for (int seed = 0; seed < total; seed++) {
                if (part[seed] >= 0 || dist[seed] > inside) {
                    continue;
                }
                int label = Math.min(labels, MAX_PARTS - 1);
                if (labels < MAX_PARTS) {
                    labels++;
                }
                int head = 0;
                int tail = 0;
                queue[tail++] = seed;
                part[seed] = (byte) label;
                while (head < tail) {
                    int i = queue[head++];
                    int ix = i / nyz;
                    int iy = (i / nz) % ny;
                    int iz = i % nz;
                    float x = minX + ix * cellX;
                    float y = minY + iy * cellY;
                    float z = minZ + iz * cellZ;
                    sumX[label] += x;
                    sumY[label] += y;
                    sumZ[label] += z;
                    count[label]++;
                    int b = label * 3;
                    low[b] = Math.min(low[b], x);
                    high[b] = Math.max(high[b], x);
                    low[b + 1] = Math.min(low[b + 1], y);
                    high[b + 1] = Math.max(high[b + 1], y);
                    low[b + 2] = Math.min(low[b + 2], z);
                    high[b + 2] = Math.max(high[b + 2], z);
                    depth[label] = Math.max(depth[label], -dist[i]);
                    int outsideNeighbours = 0;
                    for (int dir = 0; dir < 6; dir++) {
                        int j = neighbour(i, ix, iy, iz, dir);
                        if (j < 0 || dist[j] > inside) {
                            outsideNeighbours++;
                        } else if (part[j] < 0) {
                            part[j] = (byte) label;
                            queue[tail++] = j;
                        }
                    }
                    if (outsideNeighbours > 0) {
                        weight[label]++;
                    }
                }
            }

            if (labels == 0) {
                parts = 1;
                partX = new float[]{0.0f};
                partY = new float[]{0.0f};
                partZ = new float[]{0.0f};
                partAxis = new byte[]{1};
                partHalfAxis = new float[]{Math.max(-minY - MARGIN, 0.1f)};
                partRadial = new float[]{Math.max(Math.max(-minX, -minZ) - MARGIN, 0.1f)};
                partDepth = new float[]{0.0f};
                partCumulative = new float[]{1.0f};
                surfaceArea = 1.0f;
                java.util.Arrays.fill(part, (byte) 0);
                return;
            }

            parts = labels;
            partX = new float[parts];
            partY = new float[parts];
            partZ = new float[parts];
            partAxis = new byte[parts];
            partHalfAxis = new float[parts];
            partRadial = new float[parts];
            partDepth = new float[parts];
            partCumulative = new float[parts];
            float totalWeight = 0.0f;
            for (int p = 0; p < parts; p++) {
                totalWeight += Math.max(weight[p], 1.0f);
            }
            float faceArea = (cellX * cellY + cellY * cellZ + cellX * cellZ) / 3.0f;
            surfaceArea = 0.0f;
            for (int p = 0; p < parts; p++) {
                surfaceArea += weight[p] * faceArea;
            }
            surfaceArea = Math.max(surfaceArea * SURFACE_CORRECTION, 0.01f);

            float running = 0.0f;
            for (int p = 0; p < parts; p++) {
                partX[p] = sumX[p] / count[p];
                int b = p * 3;
                partY[p] = sumY[p] / count[p];
                partZ[p] = sumZ[p] / count[p];
                float sizeX = high[b] - low[b];
                float sizeY = high[b + 1] - low[b + 1];
                float sizeZ = high[b + 2] - low[b + 2];
                int axis = 1;
                float thinnest = sizeY * 0.8f;
                if (sizeX < thinnest) {
                    axis = 0;
                    thinnest = sizeX;
                }
                if (sizeZ < thinnest && sizeZ < sizeX) {
                    axis = 2;
                }
                partAxis[p] = (byte) axis;
                float size = axis == 0 ? sizeX : axis == 1 ? sizeY : sizeZ;
                partHalfAxis[p] = Math.max(size * 0.5f + Math.min(cellX, Math.min(cellY, cellZ)) * 0.5f, 0.1f);
                partDepth[p] = depth[p];
                running += Math.max(weight[p], 1.0f) / totalWeight;
                partCumulative[p] = running;
            }

            float cellMin = Math.min(cellX, Math.min(cellY, cellZ));
            for (int i = 0; i < total; i++) {
                int p = part[i];
                if (p < 0) {
                    continue;
                }
                float x = minX + (i / nyz) * cellX - partX[p];
                float y = minY + ((i / nz) % ny) * cellY - partY[p];
                float z = minZ + (i % nz) * cellZ - partZ[p];
                float a = partAxis[p] == 0 ? y : x;
                float b = partAxis[p] == 2 ? y : z;
                partRadial[p] = Math.max(partRadial[p], (float) Math.sqrt(a * a + b * b) + cellMin * 0.5f);
            }

            int head = 0;
            int tail = 0;
            for (int i = 0; i < total; i++) {
                if (part[i] >= 0) {
                    queue[tail++] = i;
                }
            }
            while (head < tail) {
                int i = queue[head++];
                int ix = i / nyz;
                int iy = (i / nz) % ny;
                int iz = i % nz;
                for (int dir = 0; dir < 6; dir++) {
                    int j = neighbour(i, ix, iy, iz, dir);
                    if (j >= 0 && part[j] < 0) {
                        part[j] = part[i];
                        queue[tail++] = j;
                    }
                }
            }
        }

        private int neighbour(int i, int ix, int iy, int iz, int dir) {
            return switch (dir) {
                case 0 -> ix > 0 ? i - ny * nz : -1;
                case 1 -> ix < nx - 1 ? i + ny * nz : -1;
                case 2 -> iy > 0 ? i - nz : -1;
                case 3 -> iy < ny - 1 ? i + nz : -1;
                case 4 -> iz > 0 ? i - 1 : -1;
                default -> iz < nz - 1 ? i + 1 : -1;
            };
        }
    }
}
