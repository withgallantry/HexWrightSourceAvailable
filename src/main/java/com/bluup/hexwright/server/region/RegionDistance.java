package com.bluup.hexwright.server.region;

import net.minecraft.world.phys.AABB;

public final class RegionDistance {

    public static final double FAR = 1.0e9;

    private static final double GRADIENT_STEP = 0.05;

    private static final int SAMPLE_GRID = 24;

    private RegionDistance() {
    }

    public static double distance(Region region, double x, double y, double z) {
        if (region instanceof Region.Sphere sphere) {
            double dx = x - sphere.center().x;
            double dy = y - sphere.center().y;
            double dz = z - sphere.center().z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz) - sphere.radius();
        }
        if (region instanceof Region.Box box) {
            double hx = (box.max().x - box.min().x) * 0.5;
            double hy = (box.max().y - box.min().y) * 0.5;
            double hz = (box.max().z - box.min().z) * 0.5;
            double qx = Math.abs(x - (box.min().x + hx)) - hx;
            double qy = Math.abs(y - (box.min().y + hy)) - hy;
            double qz = Math.abs(z - (box.min().z + hz)) - hz;
            double ox = Math.max(qx, 0.0);
            double oy = Math.max(qy, 0.0);
            double oz = Math.max(qz, 0.0);
            return Math.sqrt(ox * ox + oy * oy + oz * oz) + Math.min(Math.max(qx, Math.max(qy, qz)), 0.0);
        }
        if (region instanceof Region.Cylinder cylinder) {
            double ax = cylinder.endpointB().x - cylinder.endpointA().x;
            double ay = cylinder.endpointB().y - cylinder.endpointA().y;
            double az = cylinder.endpointB().z - cylinder.endpointA().z;
            double length = Math.sqrt(ax * ax + ay * ay + az * az);
            double ox = x - cylinder.endpointA().x;
            double oy = y - cylinder.endpointA().y;
            double oz = z - cylinder.endpointA().z;
            double along = (ox * ax + oy * ay + oz * az) / length;
            double t = along / length;
            double rx = ox - ax * t;
            double ry = oy - ay * t;
            double rz = oz - az * t;
            double radial = Math.sqrt(rx * rx + ry * ry + rz * rz) - cylinder.radius();
            double axial = Math.abs(along - length * 0.5) - length * 0.5;
            double outR = Math.max(radial, 0.0);
            double outA = Math.max(axial, 0.0);
            return Math.min(Math.max(radial, axial), 0.0) + Math.sqrt(outR * outR + outA * outA);
        }
        if (region instanceof Region.Union union) {
            return Math.min(distance(union.a(), x, y, z), distance(union.b(), x, y, z));
        }
        if (region instanceof Region.Intersection intersection) {
            return Math.max(distance(intersection.a(), x, y, z), distance(intersection.b(), x, y, z));
        }
        if (region instanceof Region.Difference difference) {
            return Math.max(distance(difference.a(), x, y, z), -distance(difference.b(), x, y, z));
        }
        if (region instanceof Region.Cut cut) {
            double nx = cut.normal().x;
            double ny = cut.normal().y;
            double nz = cut.normal().z;
            double side = ((x - cut.planePoint().x) * nx + (y - cut.planePoint().y) * ny
                + (z - cut.planePoint().z) * nz) / Math.sqrt(nx * nx + ny * ny + nz * nz);
            return Math.max(distance(cut.source(), x, y, z), cut.positiveSide() ? -side : side);
        }
        return FAR;
    }

    public static void gradient(Region region, double x, double y, double z, double[] out, int offset) {
        double h = GRADIENT_STEP;
        double gx = distance(region, x + h, y, z) - distance(region, x - h, y, z);
        double gy = distance(region, x, y + h, z) - distance(region, x, y - h, z);
        double gz = distance(region, x, y, z + h) - distance(region, x, y, z - h);
        double length = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (length < 1.0e-9) {
            out[offset] = 0.0;
            out[offset + 1] = 0.0;
            out[offset + 2] = 0.0;
            return;
        }
        out[offset] = gx / length;
        out[offset + 1] = gy / length;
        out[offset + 2] = gz / length;
    }

    public record Measure(double volume, double area) {
    }

    public static Measure measure(Region region) {
        if (region.isEmpty()) {
            return new Measure(0.0, 0.0);
        }
        if (region instanceof Region.Sphere sphere) {
            double r = sphere.radius();
            return new Measure(4.0 / 3.0 * Math.PI * r * r * r, 4.0 * Math.PI * r * r);
        }
        if (region instanceof Region.Box box) {
            double sx = box.max().x - box.min().x;
            double sy = box.max().y - box.min().y;
            double sz = box.max().z - box.min().z;
            return new Measure(sx * sy * sz, 2.0 * (sx * sy + sy * sz + sx * sz));
        }
        if (region instanceof Region.Cylinder cylinder) {
            double r = cylinder.radius();
            double length = cylinder.endpointB().subtract(cylinder.endpointA()).length();
            return new Measure(Math.PI * r * r * length, 2.0 * Math.PI * r * (r + length));
        }
        if (region instanceof Region.Union union && Region.overlap(union.a().bounds(), union.b().bounds()) == null) {
            Measure a = measure(union.a());
            Measure b = measure(union.b());
            return new Measure(a.volume + b.volume, a.area + b.area);
        }
        return sample(region);
    }

    private static Measure sample(Region region) {
        AABB bounds = region.bounds();
        int n = SAMPLE_GRID;
        double sx = Math.max(bounds.getXsize(), 1.0e-6) / n;
        double sy = Math.max(bounds.getYsize(), 1.0e-6) / n;
        double sz = Math.max(bounds.getZsize(), 1.0e-6) / n;
        boolean[] previousRow = new boolean[n * n];
        boolean[] currentRow = new boolean[n * n];
        int inside = 0;
        double transitionsArea = 0.0;
        for (int ix = 0; ix < n; ix++) {
            double x = bounds.minX + (ix + 0.5) * sx;
            for (int iy = 0; iy < n; iy++) {
                double y = bounds.minY + (iy + 0.5) * sy;
                for (int iz = 0; iz < n; iz++) {
                    double z = bounds.minZ + (iz + 0.5) * sz;
                    boolean in = region.contains(x, y, z);
                    int index = iy * n + iz;
                    currentRow[index] = in;
                    if (in) {
                        inside++;
                    }
                    if (in != (ix > 0 && previousRow[index])) {
                        transitionsArea += sy * sz;
                    }
                    if (in && ix == n - 1) {
                        transitionsArea += sy * sz;
                    }
                    if (in != (iy > 0 && currentRow[index - n])) {
                        transitionsArea += sx * sz;
                    }
                    if (in && iy == n - 1) {
                        transitionsArea += sx * sz;
                    }
                    if (in != (iz > 0 && currentRow[index - 1])) {
                        transitionsArea += sx * sy;
                    }
                    if (in && iz == n - 1) {
                        transitionsArea += sx * sy;
                    }
                }
            }
            boolean[] swap = previousRow;
            previousRow = currentRow;
            currentRow = swap;
        }
        return new Measure(inside * sx * sy * sz, transitionsArea / 1.5);
    }
}
