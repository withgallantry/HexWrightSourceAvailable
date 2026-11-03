package com.bluup.hexwright.server.path;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class Polyline {

    public static final double FINE_STEP = 0.25;

    private final Vec3[] points;
    private final double[] distances;

    private Polyline(List<Vec3> points) {
        this.points = points.toArray(new Vec3[0]);
        this.distances = new double[this.points.length];
        for (int i = 1; i < this.points.length; i++) {
            distances[i] = distances[i - 1] + this.points[i].distanceTo(this.points[i - 1]);
        }
    }

    public static final int MAX_POINTS = 4096;

    public static Polyline of(Path path) {
        return of(path, Math.max(FINE_STEP, path.length() / MAX_POINTS));
    }

    public static Polyline of(Path path, double step) {
        List<Vec3> points = new ArrayList<>();
        path.trace(points::add, step);
        return new Polyline(points);
    }

    public double length() {
        return distances[distances.length - 1];
    }

    public Vec3[] points() {
        return points;
    }

    public Vec3 at(double distance) {
        if (distance <= 0.0 || points.length == 1) {
            return points[0];
        }
        if (distance >= length()) {
            return points[points.length - 1];
        }
        int low = 0;
        int high = distances.length - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (distances[middle] <= distance) {
                low = middle;
            } else {
                high = middle;
            }
        }
        double span = distances[high] - distances[low];
        double t = span <= 0.0 ? 0.0 : (distance - distances[low]) / span;
        return points[low].lerp(points[high], t);
    }
}
