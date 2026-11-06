package com.bluup.hexwright.client.region;

import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionDistance;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.world.phys.AABB;

public record RegionGrid(double originX, double originY, double originZ, double spacing, int nx, int ny, int nz,
                         float[] distance, float[] surface, float[] normals) {

    private static final double MIN_SPACING = 0.25;
    private static final long MAX_NODES = 250_000L;

    public static RegionGrid sample(Region region, double margin) {
        AABB bounds = region.bounds();
        double spacing = MIN_SPACING;
        while ((long) nodes(bounds.getXsize(), margin, spacing) * nodes(bounds.getYsize(), margin, spacing)
            * nodes(bounds.getZsize(), margin, spacing) > MAX_NODES) {
            spacing *= 1.1;
        }
        int nx = nodes(bounds.getXsize(), margin, spacing);
        int ny = nodes(bounds.getYsize(), margin, spacing);
        int nz = nodes(bounds.getZsize(), margin, spacing);
        double originX = bounds.minX - margin;
        double originY = bounds.minY - margin;
        double originZ = bounds.minZ - margin;

        float[] distance = new float[nx * ny * nz];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    distance[(i * ny + j) * nz + k] = (float) RegionDistance.distance(region,
                        originX + i * spacing, originY + j * spacing, originZ + k * spacing);
                }
            }
        }

        FloatArrayList surface = new FloatArrayList();
        FloatArrayList normals = new FloatArrayList();
        double[] normal = new double[3];
        float[] crossing = new float[3];
        for (int i = 0; i < nx - 1; i++) {
            for (int j = 0; j < ny - 1; j++) {
                for (int k = 0; k < nz - 1; k++) {
                    if (!crossing(distance, ny, nz, i, j, k, crossing)) {
                        continue;
                    }
                    float x = (float) ((i + crossing[0]) * spacing);
                    float y = (float) ((j + crossing[1]) * spacing);
                    float z = (float) ((k + crossing[2]) * spacing);
                    surface.add(x);
                    surface.add(y);
                    surface.add(z);
                    RegionDistance.gradient(region, originX + x, originY + y, originZ + z, normal, 0);
                    normals.add((float) normal[0]);
                    normals.add((float) normal[1]);
                    normals.add((float) normal[2]);
                }
            }
        }
        return new RegionGrid(originX, originY, originZ, spacing, nx, ny, nz, distance, surface.toFloatArray(),
            normals.toFloatArray());
    }

    public int surfaceCount() {
        return surface.length / 3;
    }

    public AABB box() {
        return new AABB(originX, originY, originZ,
            originX + (nx - 1) * spacing, originY + (ny - 1) * spacing, originZ + (nz - 1) * spacing);
    }

    private static int nodes(double size, double margin, double spacing) {
        return (int) Math.ceil((size + 2.0 * margin) / spacing) + 1;
    }

    private static boolean crossing(float[] field, int ny, int nz, int i, int j, int k, float[] out) {
        float[] corner = new float[8];
        int inside = 0;
        for (int c = 0; c < 8; c++) {
            corner[c] = field[((i + (c >> 2)) * ny + j + ((c >> 1) & 1)) * nz + k + (c & 1)];
            if (corner[c] < 0.0F) {
                inside++;
            }
        }
        if (inside == 0 || inside == 8) {
            return false;
        }
        out[0] = 0.0F;
        out[1] = 0.0F;
        out[2] = 0.0F;
        int crossings = 0;
        for (int a = 0; a < 8; a++) {
            for (int bit = 0; bit < 3; bit++) {
                int b = a | (4 >> bit);
                if (b == a || (corner[a] < 0.0F) == (corner[b] < 0.0F)) {
                    continue;
                }
                float t = corner[a] / (corner[a] - corner[b]);
                out[0] += bit == 0 ? t : (a >> 2);
                out[1] += bit == 1 ? t : ((a >> 1) & 1);
                out[2] += bit == 2 ? t : (a & 1);
                crossings++;
            }
        }
        out[0] /= crossings;
        out[1] /= crossings;
        out[2] /= crossings;
        return true;
    }
}
