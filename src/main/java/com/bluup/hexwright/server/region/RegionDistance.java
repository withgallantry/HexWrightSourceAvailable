package com.bluup.hexwright.server.region;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RegionDistance {

    public static final double FAR = 1.0e9;

    private static final double GRADIENT_STEP = 0.05;

    private static final int SAMPLE_GRID = 24;

    private RegionDistance() {
    }

    public static double distance(Region region, double x, double y, double z) {
        return evaluate(prepared(region), x, y, z);
    }

    private static double evaluate(Region region, double x, double y, double z) {
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
        if (region instanceof Region.Cone cone) {
            return coneDistance(cone, x, y, z);
        }
        if (region instanceof Region.Torus torus) {
            double dx = x - torus.center().x;
            double dy = y - torus.center().y;
            double dz = z - torus.center().z;
            double height = dx * torus.axis().x + dy * torus.axis().y + dz * torus.axis().z;
            double fromRing = Math.sqrt(Math.max(0.0, dx * dx + dy * dy + dz * dz - height * height))
                - torus.majorRadius();
            return Math.sqrt(fromRing * fromRing + height * height) - torus.minorRadius();
        }
        if (region instanceof Region.Union union) {
            return Math.min(evaluate(union.a(), x, y, z), evaluate(union.b(), x, y, z));
        }
        if (region instanceof Region.Intersection intersection) {
            return Math.max(evaluate(intersection.a(), x, y, z), evaluate(intersection.b(), x, y, z));
        }
        if (region instanceof Region.Difference difference) {
            return Math.max(evaluate(difference.a(), x, y, z), -evaluate(difference.b(), x, y, z));
        }
        if (region instanceof Region.Cut cut) {
            double nx = cut.normal().x;
            double ny = cut.normal().y;
            double nz = cut.normal().z;
            double side = ((x - cut.planePoint().x) * nx + (y - cut.planePoint().y) * ny
                + (z - cut.planePoint().z) * nz) / Math.sqrt(nx * nx + ny * ny + nz * nz);
            return Math.max(evaluate(cut.source(), x, y, z), cut.positiveSide() ? -side : side);
        }
        return FAR;
    }

    private static double coneDistance(Region.Cone cone, double x, double y, double z) {
        double ax = cone.endpointB().x - cone.endpointA().x;
        double ay = cone.endpointB().y - cone.endpointA().y;
        double az = cone.endpointB().z - cone.endpointA().z;
        double ox = x - cone.endpointA().x;
        double oy = y - cone.endpointA().y;
        double oz = z - cone.endpointA().z;
        double radiusA = cone.radiusA();
        double radiusB = cone.radiusB();
        double taper = radiusB - radiusA;
        double lengthSqr = ax * ax + ay * ay + az * az;
        double along = (ox * ax + oy * ay + oz * az) / lengthSqr;
        double radial = Math.sqrt(Math.max(0.0, ox * ox + oy * oy + oz * oz - along * along * lengthSqr));
        double capRadial = Math.max(0.0, radial - (along < 0.5 ? radiusA : radiusB));
        double capAlong = Math.abs(along - 0.5) - 0.5;
        double slope = Math.max(0.0, Math.min(1.0, (taper * (radial - radiusA) + along * lengthSqr) / (taper * taper + lengthSqr)));
        double sideRadial = radial - radiusA - slope * taper;
        double sideAlong = along - slope;
        double sign = sideRadial < 0.0 && capAlong < 0.0 ? -1.0 : 1.0;
        return sign * Math.sqrt(Math.min(
            capRadial * capRadial + capAlong * capAlong * lengthSqr,
            sideRadial * sideRadial + sideAlong * sideAlong * lengthSqr));
    }

    public static void gradient(Region region, double x, double y, double z, double[] out, int offset) {
        Region shape = prepared(region);
        double h = GRADIENT_STEP;
        double gx = evaluate(shape, x + h, y, z) - evaluate(shape, x - h, y, z);
        double gy = evaluate(shape, x, y + h, z) - evaluate(shape, x, y - h, z);
        double gz = evaluate(shape, x, y, z + h) - evaluate(shape, x, y, z - h);
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


    private static final double[] PUSH_DEPTHS = {0.02, 0.25, 0.5, 1.0, 1.5, 2.0};
    private static final double PUSH_SPACING = 0.5;
    private static final int PUSH_SAMPLES = 12;
    private static final double PUSH_INSET = 0.01;

    private record Prepared(WeakReference<Region> key, Region shape) {
    }

    private static final AtomicReferenceArray<Prepared> PREPARED = new AtomicReferenceArray<>(32);
    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();

    private static Region prepared(Region region) {
        if (!(region instanceof Region.Difference || region instanceof Region.Union
            || region instanceof Region.Intersection || region instanceof Region.Cut)) {
            return region;
        }
        for (int i = 0; i < PREPARED.length(); i++) {
            Prepared entry = PREPARED.get(i);
            if (entry != null && entry.key.get() == region) {
                return entry.shape;
            }
        }
        Region shape = prepare(region);
        PREPARED.set(Math.floorMod(NEXT_SLOT.getAndIncrement(), PREPARED.length()),
            new Prepared(new WeakReference<>(region), shape));
        return shape;
    }

    static Region prepare(Region region) {
        if (region instanceof Region.Union union) {
            return new Region.Union(prepare(union.a()), prepare(union.b()));
        }
        if (region instanceof Region.Intersection intersection) {
            return new Region.Intersection(prepare(intersection.a()), prepare(intersection.b()));
        }
        if (region instanceof Region.Cut cut) {
            return new Region.Cut(prepare(cut.source()), cut.planePoint(), cut.normal(), cut.positiveSide());
        }
        if (region instanceof Region.Difference difference) {
            Region minuend = difference.a();
            return new Region.Difference(prepare(minuend), extend(prepare(difference.b()), minuend));
        }
        return region;
    }

    private interface Layer {
        boolean clear(Region minuend, double depth);
    }

    private static double push(Region minuend, Layer layer) {
        double pushed = 0.0;
        for (double depth : PUSH_DEPTHS) {
            if (!layer.clear(minuend, depth)) {
                break;
            }
            pushed = depth;
        }
        return pushed;
    }

    private static Region extend(Region subtrahend, Region minuend) {
        if (subtrahend instanceof Region.Union union) {
            return new Region.Union(extend(union.a(), minuend), extend(union.b(), minuend));
        }
        if (subtrahend instanceof Region.Box box) {
            return extendBox(box, minuend);
        }
        if (subtrahend instanceof Region.Cylinder cylinder) {
            return extendCylinder(cylinder, minuend);
        }
        if (subtrahend instanceof Region.Cone cone) {
            return extendCone(cone, minuend);
        }
        if (subtrahend instanceof Region.Sphere sphere) {
            double push = push(minuend, (m, depth) -> sphereClear(m, sphere.center(), sphere.radius() + depth));
            return push > 0.0 ? new Region.Sphere(sphere.center(), sphere.radius() + push) : sphere;
        }
        return subtrahend;
    }

    private static Region extendBox(Region.Box box, Region minuend) {
        double[] lo = {box.min().x, box.min().y, box.min().z};
        double[] hi = {box.max().x, box.max().y, box.max().z};
        boolean changed = false;
        for (int axis = 0; axis < 3; axis++) {
            for (int side = -1; side <= 1; side += 2) {
                int face = axis;
                int sign = side;
                double push = push(minuend, (m, depth) -> boxFaceClear(m, lo, hi, face, sign, depth));
                if (push > 0.0) {
                    changed = true;
                    if (side < 0) {
                        lo[axis] -= push;
                    } else {
                        hi[axis] += push;
                    }
                }
            }
        }
        return changed ? new Region.Box(new Vec3(lo[0], lo[1], lo[2]), new Vec3(hi[0], hi[1], hi[2])) : box;
    }

    private static boolean boxFaceClear(Region minuend, double[] lo, double[] hi, int axis, int side, double depth) {
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        int nu = samples(hi[u] - lo[u]);
        int nv = samples(hi[v] - lo[v]);
        double[] p = new double[3];
        p[axis] = side < 0 ? lo[axis] - depth : hi[axis] + depth;
        for (int i = 0; i < nu; i++) {
            p[u] = spread(lo[u], hi[u], i, nu);
            for (int j = 0; j < nv; j++) {
                p[v] = spread(lo[v], hi[v], j, nv);
                if (minuend.contains(p[0], p[1], p[2])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Region extendCylinder(Region.Cylinder cylinder, Region minuend) {
        Vec3 a = cylinder.endpointA();
        Vec3 b = cylinder.endpointB();
        double r = cylinder.radius();
        Vec3 w = b.subtract(a).normalize();
        Vec3 helper = Math.abs(w.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 u = w.cross(helper).normalize();
        Vec3 v = w.cross(u);
        double pushA = push(minuend, (m, depth) -> discClear(m, a.subtract(w.scale(depth)), u, v, r));
        double pushB = push(minuend, (m, depth) -> discClear(m, b.add(w.scale(depth)), u, v, r));
        Vec3 a2 = a.subtract(w.scale(pushA));
        Vec3 b2 = b.add(w.scale(pushB));
        double pushR = push(minuend, (m, depth) -> tubeClear(m, a2, b2, u, v, r + depth));
        if (pushA == 0.0 && pushB == 0.0 && pushR == 0.0) {
            return cylinder;
        }
        return new Region.Cylinder(a2, b2, r + pushR);
    }

    private static Region extendCone(Region.Cone cone, Region minuend) {
        Vec3 a = cone.endpointA();
        Vec3 b = cone.endpointB();
        double length = b.subtract(a).length();
        Vec3 w = b.subtract(a).scale(1.0 / length);
        Vec3 helper = Math.abs(w.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 u = w.cross(helper).normalize();
        Vec3 v = w.cross(u);
        double slope = (cone.radiusB() - cone.radiusA()) / length;
        double reachA = slope > 0.0 ? cone.radiusA() / slope : Double.POSITIVE_INFINITY;
        double reachB = slope < 0.0 ? cone.radiusB() / -slope : Double.POSITIVE_INFINITY;
        double pushA = push(minuend, (m, depth) -> depth <= reachA
            && discClear(m, a.subtract(w.scale(depth)), u, v, Math.max(0.0, cone.radiusA() - slope * depth)));
        double pushB = push(minuend, (m, depth) -> depth <= reachB
            && discClear(m, b.add(w.scale(depth)), u, v, Math.max(0.0, cone.radiusB() + slope * depth)));
        if (pushA == 0.0 && pushB == 0.0) {
            return cone;
        }
        return new Region.Cone(a.subtract(w.scale(pushA)), b.add(w.scale(pushB)),
            Math.max(0.0, cone.radiusA() - slope * pushA), Math.max(0.0, cone.radiusB() + slope * pushB));
    }

    private static boolean discClear(Region minuend, Vec3 centre, Vec3 u, Vec3 v, double radius) {
        if (minuend.contains(centre.x, centre.y, centre.z)) {
            return false;
        }
        int rings = Math.max(1, Math.min(PUSH_SAMPLES / 2, (int) Math.ceil(radius / PUSH_SPACING)));
        for (int ring = 1; ring <= rings; ring++) {
            double rho = Math.max(radius - PUSH_INSET, 0.0) * ring / rings;
            if (!ringClear(minuend, centre, u, v, rho)) {
                return false;
            }
        }
        return true;
    }

    private static boolean tubeClear(Region minuend, Vec3 a, Vec3 b, Vec3 u, Vec3 v, double radius) {
        Vec3 axis = b.subtract(a);
        int steps = samples(axis.length());
        for (int i = 0; i < steps; i++) {
            double t = (double) i / (steps - 1);
            if (!ringClear(minuend, a.add(axis.scale(t)), u, v, radius)) {
                return false;
            }
        }
        return true;
    }

    private static boolean ringClear(Region minuend, Vec3 centre, Vec3 u, Vec3 v, double radius) {
        int count = Math.max(8, Math.min(4 * PUSH_SAMPLES, (int) Math.ceil(2.0 * Math.PI * radius / PUSH_SPACING)));
        for (int k = 0; k < count; k++) {
            double angle = 2.0 * Math.PI * k / count;
            double cu = Math.cos(angle) * radius;
            double cv = Math.sin(angle) * radius;
            if (minuend.contains(centre.x + u.x * cu + v.x * cv, centre.y + u.y * cu + v.y * cv,
                centre.z + u.z * cu + v.z * cv)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sphereClear(Region minuend, Vec3 centre, double radius) {
        int count = Math.max(16, Math.min(PUSH_SAMPLES * PUSH_SAMPLES,
            (int) Math.ceil(4.0 * Math.PI * radius * radius / (PUSH_SPACING * PUSH_SPACING))));
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int k = 0; k < count; k++) {
            double y = 1.0 - 2.0 * (k + 0.5) / count;
            double ring = Math.sqrt(1.0 - y * y);
            double angle = golden * k;
            if (minuend.contains(centre.x + Math.cos(angle) * ring * radius, centre.y + y * radius,
                centre.z + Math.sin(angle) * ring * radius)) {
                return false;
            }
        }
        return true;
    }

    private static int samples(double length) {
        return Math.max(2, Math.min(PUSH_SAMPLES, (int) Math.ceil(length / PUSH_SPACING) + 1));
    }

    private static double spread(double lo, double hi, int i, int n) {
        if (hi - lo <= 2.0 * PUSH_INSET) {
            return (lo + hi) * 0.5;
        }
        return lo + PUSH_INSET + (hi - lo - 2.0 * PUSH_INSET) * i / (n - 1);
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
        if (region instanceof Region.Cone cone) {
            double ra = cone.radiusA();
            double rb = cone.radiusB();
            double length = cone.endpointB().subtract(cone.endpointA()).length();
            double slant = Math.sqrt(length * length + (ra - rb) * (ra - rb));
            return new Measure(Math.PI * length / 3.0 * (ra * ra + ra * rb + rb * rb),
                Math.PI * ((ra + rb) * slant + ra * ra + rb * rb));
        }
        if (region instanceof Region.Torus torus && torus.minorRadius() <= torus.majorRadius()) {
            double major = torus.majorRadius();
            double minor = torus.minorRadius();
            return new Measure(2.0 * Math.PI * Math.PI * major * minor * minor, 4.0 * Math.PI * Math.PI * major * minor);
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
