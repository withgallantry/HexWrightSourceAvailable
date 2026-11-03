package com.bluup.hexwright.client.region;

import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionDistance;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

public record RegionShell(double originX, double originY, double originZ, float[] positions, float[] normals,
                          int[] quads, AABB box) {

    private static final double MIN_SPACING = 0.25;
    private static final int MAX_NODES = 250_000;
    private static final int PAD = 1;
    private static final int[][] AROUND_EDGE = {{-1, -1}, {0, -1}, {0, 0}, {-1, 0}};

    public int vertexCount() {
        return positions.length / 3;
    }

    public static RegionShell trace(Region region) {
        AABB bounds = region.bounds();
        double spacing = MIN_SPACING;
        while (nodes(bounds.getXsize(), spacing) * nodes(bounds.getYsize(), spacing) * nodes(bounds.getZsize(), spacing)
            > MAX_NODES) {
            spacing *= 1.1;
        }
        int nx = (int) nodes(bounds.getXsize(), spacing);
        int ny = (int) nodes(bounds.getYsize(), spacing);
        int nz = (int) nodes(bounds.getZsize(), spacing);
        double originX = bounds.minX - PAD * spacing;
        double originY = bounds.minY - PAD * spacing;
        double originZ = bounds.minZ - PAD * spacing;

        float[] field = new float[nx * ny * nz];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    field[(i * ny + j) * nz + k] = (float) RegionDistance.distance(region,
                        originX + i * spacing, originY + j * spacing, originZ + k * spacing);
                }
            }
        }

        int cy = ny - 1;
        int cz = nz - 1;
        int[] cellVertex = new int[(nx - 1) * cy * cz];
        Arrays.fill(cellVertex, -1);
        FloatArrayList positions = new FloatArrayList();
        FloatArrayList normals = new FloatArrayList();
        double[] normal = new double[3];
        for (int i = 0; i < nx - 1; i++) {
            for (int j = 0; j < cy; j++) {
                for (int k = 0; k < cz; k++) {
                    float[] crossing = crossing(field, ny, nz, i, j, k);
                    if (crossing == null) {
                        continue;
                    }
                    cellVertex[(i * cy + j) * cz + k] = positions.size() / 3;
                    float x = (float) ((i + crossing[0]) * spacing);
                    float y = (float) ((j + crossing[1]) * spacing);
                    float z = (float) ((k + crossing[2]) * spacing);
                    positions.add(x);
                    positions.add(y);
                    positions.add(z);
                    RegionDistance.gradient(region, originX + x, originY + y, originZ + z, normal, 0);
                    normals.add((float) normal[0]);
                    normals.add((float) normal[1]);
                    normals.add((float) normal[2]);
                }
            }
        }

        IntArrayList quads = new IntArrayList();
        int[] size = {nx, ny, nz};
        int[] stride = {ny * nz, nz, 1};
        int[] node = new int[3];
        int[] cell = new int[3];
        for (node[0] = 0; node[0] < nx; node[0]++) {
            for (node[1] = 0; node[1] < ny; node[1]++) {
                for (node[2] = 0; node[2] < nz; node[2]++) {
                    int index = (node[0] * ny + node[1]) * nz + node[2];
                    boolean inside = field[index] < 0.0F;
                    for (int axis = 0; axis < 3; axis++) {
                        int v = (axis + 1) % 3;
                        int w = (axis + 2) % 3;
                        if (node[axis] + 1 >= size[axis] || node[v] < 1 || node[w] < 1
                            || node[v] >= size[v] - 1 || node[w] >= size[w] - 1
                            || inside == field[index + stride[axis]] < 0.0F) {
                            continue;
                        }
                        int start = quads.size();
                        for (int[] step : AROUND_EDGE) {
                            cell[axis] = node[axis];
                            cell[v] = node[v] + step[0];
                            cell[w] = node[w] + step[1];
                            int vertex = cellVertex[(cell[0] * cy + cell[1]) * cz + cell[2]];
                            if (vertex < 0) {
                                break;
                            }
                            quads.add(vertex);
                        }
                        if (quads.size() - start != 4) {
                            quads.size(start);
                        }
                    }
                }
            }
        }

        AABB box = new AABB(originX, originY, originZ,
            originX + (nx - 1) * spacing, originY + (ny - 1) * spacing, originZ + (nz - 1) * spacing);
        return new RegionShell(originX, originY, originZ, positions.toFloatArray(), normals.toFloatArray(),
            quads.toIntArray(), box);
    }

    private static double nodes(double size, double spacing) {
        return Math.ceil(size / spacing) + 1 + 2 * PAD;
    }

    private static float[] crossing(float[] field, int ny, int nz, int i, int j, int k) {
        float[] corner = new float[8];
        int inside = 0;
        for (int c = 0; c < 8; c++) {
            corner[c] = field[((i + (c >> 2)) * ny + j + ((c >> 1) & 1)) * nz + k + (c & 1)];
            if (corner[c] < 0.0F) {
                inside++;
            }
        }
        if (inside == 0 || inside == 8) {
            return null;
        }
        float[] sum = new float[3];
        int crossings = 0;
        for (int a = 0; a < 8; a++) {
            for (int bit = 0; bit < 3; bit++) {
                int b = a | (4 >> bit);
                if (b == a || (corner[a] < 0.0F) == (corner[b] < 0.0F)) {
                    continue;
                }
                float t = corner[a] / (corner[a] - corner[b]);
                sum[0] += bit == 0 ? t : (a >> 2);
                sum[1] += bit == 1 ? t : ((a >> 1) & 1);
                sum[2] += bit == 2 ? t : (a & 1);
                crossings++;
            }
        }
        sum[0] /= crossings;
        sum[1] /= crossings;
        sum[2] /= crossings;
        return sum;
    }
}
