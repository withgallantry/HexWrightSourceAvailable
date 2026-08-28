package com.bluup.hexwright.server.region;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public sealed interface Region {

    Region EMPTY = new Empty();

    int MAX_DEPTH = 64;

    String TAG_FORM = "Form";

    boolean contains(Vec3 point);

    default boolean contains(double x, double y, double z) {
        return contains(new Vec3(x, y, z));
    }

    AABB bounds();

    int nodeCount();

    String describe();

    CompoundTag save();

    default boolean isEmpty() {
        return false;
    }


    static Region sphere(Vec3 center, double radius) {
        return new Sphere(center, radius);
    }

    static Region box(Vec3 cornerA, Vec3 cornerB) {
        return new Box(
            new Vec3(Math.min(cornerA.x, cornerB.x), Math.min(cornerA.y, cornerB.y), Math.min(cornerA.z, cornerB.z)),
            new Vec3(Math.max(cornerA.x, cornerB.x), Math.max(cornerA.y, cornerB.y), Math.max(cornerA.z, cornerB.z)));
    }

    static Region cylinder(Vec3 endpointA, Vec3 endpointB, double radius) {
        return new Cylinder(endpointA, endpointB, radius);
    }


    static Region union(Region a, Region b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return new Union(a, b);
    }

    static Region intersection(Region a, Region b) {
        if (a.isEmpty() || b.isEmpty() || overlap(a.bounds(), b.bounds()) == null) {
            return EMPTY;
        }
        return new Intersection(a, b);
    }

    static Region difference(Region a, Region b) {
        if (a.isEmpty()) {
            return EMPTY;
        }
        if (b.isEmpty() || overlap(a.bounds(), b.bounds()) == null) {
            return a;
        }
        return new Difference(a, b);
    }

    static Region cut(Region source, Vec3 planePoint, Vec3 normal, boolean positiveSide) {
        if (source.isEmpty() || clip(source.bounds(), planePoint, normal, positiveSide) == null) {
            return EMPTY;
        }
        return new Cut(source, planePoint, normal, positiveSide);
    }


    record Empty() implements Region {
        @Override
        public boolean contains(Vec3 point) {
            return false;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return false;
        }

        @Override
        public AABB bounds() {
            return new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int nodeCount() {
            return 1;
        }

        @Override
        public String describe() {
            return "empty";
        }

        @Override
        public CompoundTag save() {
            return form("empty");
        }
    }

    record Sphere(Vec3 center, double radius) implements Region {
        public Sphere {
            requireFinite(center);
            if (!Double.isFinite(radius) || radius < 0.0) {
                throw new IllegalArgumentException("Sphere radius must be finite and non-negative");
            }
        }

        @Override
        public boolean contains(Vec3 point) {
            return point.distanceToSqr(center) <= radius * radius;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double dx = x - center.x;
            double dy = y - center.y;
            double dz = z - center.z;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }

        @Override
        public AABB bounds() {
            return new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        }

        @Override
        public int nodeCount() {
            return 1;
        }

        @Override
        public String describe() {
            return "sphere r=" + trim(radius);
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = form("sphere");
            putVec(tag, "Center", center);
            tag.putDouble("Radius", radius);
            return tag;
        }
    }

    record Box(Vec3 min, Vec3 max) implements Region {
        public Box {
            requireFinite(min);
            requireFinite(max);
            if (min.x > max.x || min.y > max.y || min.z > max.z) {
                throw new IllegalArgumentException("Box corners must be sorted");
            }
        }

        @Override
        public boolean contains(Vec3 point) {
            return point.x >= min.x && point.x <= max.x
                && point.y >= min.y && point.y <= max.y
                && point.z >= min.z && point.z <= max.z;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return x >= min.x && x <= max.x
                && y >= min.y && y <= max.y
                && z >= min.z && z <= max.z;
        }

        @Override
        public AABB bounds() {
            return new AABB(min.x, min.y, min.z, max.x, max.y, max.z);
        }

        @Override
        public int nodeCount() {
            return 1;
        }

        @Override
        public String describe() {
            return "box " + trim(max.x - min.x) + "x" + trim(max.y - min.y) + "x" + trim(max.z - min.z);
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = form("box");
            putVec(tag, "Min", min);
            putVec(tag, "Max", max);
            return tag;
        }
    }

    record Cylinder(Vec3 endpointA, Vec3 endpointB, double radius) implements Region {
        public static final double MIN_AXIS_LENGTH_SQR = 1.0e-8;

        public Cylinder {
            requireFinite(endpointA);
            requireFinite(endpointB);
            if (!Double.isFinite(radius) || radius < 0.0) {
                throw new IllegalArgumentException("Cylinder radius must be finite and non-negative");
            }
            if (endpointB.subtract(endpointA).lengthSqr() < MIN_AXIS_LENGTH_SQR) {
                throw new IllegalArgumentException("Cylinder axis is degenerate");
            }
        }

        @Override
        public boolean contains(Vec3 point) {
            Vec3 axis = endpointB.subtract(endpointA);
            Vec3 offset = point.subtract(endpointA);
            double lengthSqr = axis.lengthSqr();
            double along = offset.dot(axis) / lengthSqr;
            if (along < 0.0 || along > 1.0) {
                return false;
            }
            return offset.subtract(axis.scale(along)).lengthSqr() <= radius * radius;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double axisX = endpointB.x - endpointA.x;
            double axisY = endpointB.y - endpointA.y;
            double axisZ = endpointB.z - endpointA.z;
            double offsetX = x - endpointA.x;
            double offsetY = y - endpointA.y;
            double offsetZ = z - endpointA.z;
            double lengthSqr = axisX * axisX + axisY * axisY + axisZ * axisZ;
            double along = (offsetX * axisX + offsetY * axisY + offsetZ * axisZ) / lengthSqr;
            if (along < 0.0 || along > 1.0) {
                return false;
            }
            double radialX = offsetX - axisX * along;
            double radialY = offsetY - axisY * along;
            double radialZ = offsetZ - axisZ * along;
            return radialX * radialX + radialY * radialY + radialZ * radialZ <= radius * radius;
        }

        @Override
        public AABB bounds() {
            Vec3 axis = endpointB.subtract(endpointA);
            double lengthSqr = axis.lengthSqr();
            double spreadX = radius * Math.sqrt(Math.max(0.0, 1.0 - axis.x * axis.x / lengthSqr));
            double spreadY = radius * Math.sqrt(Math.max(0.0, 1.0 - axis.y * axis.y / lengthSqr));
            double spreadZ = radius * Math.sqrt(Math.max(0.0, 1.0 - axis.z * axis.z / lengthSqr));
            return new AABB(
                Math.min(endpointA.x, endpointB.x) - spreadX,
                Math.min(endpointA.y, endpointB.y) - spreadY,
                Math.min(endpointA.z, endpointB.z) - spreadZ,
                Math.max(endpointA.x, endpointB.x) + spreadX,
                Math.max(endpointA.y, endpointB.y) + spreadY,
                Math.max(endpointA.z, endpointB.z) + spreadZ);
        }

        @Override
        public int nodeCount() {
            return 1;
        }

        @Override
        public String describe() {
            return "cylinder r=" + trim(radius) + " l=" + trim(endpointB.subtract(endpointA).length());
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = form("cylinder");
            putVec(tag, "A", endpointA);
            putVec(tag, "B", endpointB);
            tag.putDouble("Radius", radius);
            return tag;
        }
    }

    record Union(Region a, Region b) implements Region {
        @Override
        public boolean contains(Vec3 point) {
            return a.contains(point) || b.contains(point);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return a.contains(x, y, z) || b.contains(x, y, z);
        }

        @Override
        public AABB bounds() {
            return a.bounds().minmax(b.bounds());
        }

        @Override
        public int nodeCount() {
            return a.nodeCount() + b.nodeCount() + 1;
        }

        @Override
        public String describe() {
            return "(" + a.describe() + " | " + b.describe() + ")";
        }

        @Override
        public CompoundTag save() {
            return operands("union", a, b);
        }
    }

    record Intersection(Region a, Region b) implements Region {
        @Override
        public boolean contains(Vec3 point) {
            return a.contains(point) && b.contains(point);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return a.contains(x, y, z) && b.contains(x, y, z);
        }

        @Override
        public AABB bounds() {
            AABB overlap = overlap(a.bounds(), b.bounds());
            return overlap != null ? overlap : a.bounds();
        }

        @Override
        public int nodeCount() {
            return a.nodeCount() + b.nodeCount() + 1;
        }

        @Override
        public String describe() {
            return "(" + a.describe() + " & " + b.describe() + ")";
        }

        @Override
        public CompoundTag save() {
            return operands("intersection", a, b);
        }
    }

    record Difference(Region a, Region b) implements Region {
        @Override
        public boolean contains(Vec3 point) {
            return a.contains(point) && !b.contains(point);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return a.contains(x, y, z) && !b.contains(x, y, z);
        }

        @Override
        public AABB bounds() {
            return a.bounds();
        }

        @Override
        public int nodeCount() {
            return a.nodeCount() + b.nodeCount() + 1;
        }

        @Override
        public String describe() {
            return "(" + a.describe() + " \\ " + b.describe() + ")";
        }

        @Override
        public CompoundTag save() {
            return operands("difference", a, b);
        }
    }

    record Cut(Region source, Vec3 planePoint, Vec3 normal, boolean positiveSide) implements Region {
        public static final double MIN_NORMAL_LENGTH_SQR = 1.0e-12;

        public Cut {
            requireFinite(planePoint);
            requireFinite(normal);
            if (normal.lengthSqr() < MIN_NORMAL_LENGTH_SQR) {
                throw new IllegalArgumentException("Cutting plane normal is degenerate");
            }
        }

        @Override
        public boolean contains(Vec3 point) {
            double side = point.subtract(planePoint).dot(normal);
            return (positiveSide ? side > 0.0 : side <= 0.0) && source.contains(point);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double side = (x - planePoint.x) * normal.x
                + (y - planePoint.y) * normal.y
                + (z - planePoint.z) * normal.z;
            return (positiveSide ? side > 0.0 : side <= 0.0) && source.contains(x, y, z);
        }

        @Override
        public AABB bounds() {
            AABB clipped = clip(source.bounds(), planePoint, normal, positiveSide);
            return clipped != null ? clipped : source.bounds();
        }

        @Override
        public int nodeCount() {
            return source.nodeCount() + 1;
        }

        @Override
        public String describe() {
            return "cut(" + source.describe() + ")";
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = form("cut");
            tag.put("Source", source.save());
            putVec(tag, "Point", planePoint);
            putVec(tag, "Normal", normal);
            tag.putBoolean("Positive", positiveSide);
            return tag;
        }
    }


    static Region load(CompoundTag tag) {
        return load(tag, 0);
    }

    private static Region load(CompoundTag tag, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Region nested deeper than " + MAX_DEPTH);
        }
        String form = tag.getString(TAG_FORM);
        return switch (form) {
            case "empty" -> EMPTY;
            case "sphere" -> new Sphere(getVec(tag, "Center"), tag.getDouble("Radius"));
            case "box" -> box(getVec(tag, "Min"), getVec(tag, "Max"));
            case "cylinder" -> new Cylinder(getVec(tag, "A"), getVec(tag, "B"), tag.getDouble("Radius"));
            case "union" -> union(load(tag.getCompound("A"), depth + 1), load(tag.getCompound("B"), depth + 1));
            case "intersection" ->
                intersection(load(tag.getCompound("A"), depth + 1), load(tag.getCompound("B"), depth + 1));
            case "difference" ->
                difference(load(tag.getCompound("A"), depth + 1), load(tag.getCompound("B"), depth + 1));
            case "cut" -> cut(load(tag.getCompound("Source"), depth + 1),
                getVec(tag, "Point"), getVec(tag, "Normal"), tag.getBoolean("Positive"));
            default -> throw new IllegalArgumentException("Unknown region form: " + form);
        };
    }

    private static CompoundTag form(String form) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_FORM, form);
        return tag;
    }

    private static CompoundTag operands(String form, Region a, Region b) {
        CompoundTag tag = form(form);
        tag.put("A", a.save());
        tag.put("B", b.save());
        return tag;
    }

    private static void putVec(CompoundTag tag, String key, Vec3 vec) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(vec.x));
        list.add(DoubleTag.valueOf(vec.y));
        list.add(DoubleTag.valueOf(vec.z));
        tag.put(key, list);
    }

    private static Vec3 getVec(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_DOUBLE);
        if (list.size() != 3) {
            throw new IllegalArgumentException("Expected three coordinates for " + key);
        }
        return new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
    }


    private static void requireFinite(Vec3 vec) {
        if (!Double.isFinite(vec.x) || !Double.isFinite(vec.y) || !Double.isFinite(vec.z)) {
            throw new IllegalArgumentException("Region coordinates must be finite");
        }
    }

    @Nullable
    static AABB overlap(AABB first, AABB second) {
        double minX = Math.max(first.minX, second.minX);
        double minY = Math.max(first.minY, second.minY);
        double minZ = Math.max(first.minZ, second.minZ);
        double maxX = Math.min(first.maxX, second.maxX);
        double maxY = Math.min(first.maxY, second.maxY);
        double maxZ = Math.min(first.maxZ, second.maxZ);
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nullable
    static AABB clip(AABB box, Vec3 planePoint, Vec3 normal, boolean positiveSide) {
        Vec3[] corners = new Vec3[8];
        double[] sides = new double[8];
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3(
                (i & 1) == 0 ? box.minX : box.maxX,
                (i & 2) == 0 ? box.minY : box.maxY,
                (i & 4) == 0 ? box.minZ : box.maxZ);
            corners[i] = corner;
            sides[i] = corner.subtract(planePoint).dot(normal);
        }

        List<Vec3> kept = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            if (positiveSide ? sides[i] >= 0.0 : sides[i] <= 0.0) {
                kept.add(corners[i]);
            }
        }
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                int j = i | bit;
                if (j == i) {
                    continue;
                }
                double from = sides[i];
                double to = sides[j];
                if ((from < 0.0 && to > 0.0) || (from > 0.0 && to < 0.0)) {
                    double t = from / (from - to);
                    kept.add(corners[i].add(corners[j].subtract(corners[i]).scale(t)));
                }
            }
        }
        if (kept.isEmpty()) {
            return null;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 point : kept) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static String trim(double value) {
        return value == Math.floor(value) && Double.isFinite(value)
            ? Long.toString((long) value)
            : String.format("%.2f", value);
    }
}
