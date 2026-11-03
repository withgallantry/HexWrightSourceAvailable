package com.bluup.hexwright.server.path;

import com.bluup.hexwright.server.region.Region;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public sealed interface Path {

    int MAX_PIECES = 64;

    double MAX_COORDINATE = 3.2e7;

    String TAG_FORM = "Form";

    Vec3 start();

    Vec3 end();

    void trace(Consumer<Vec3> out, double step);

    int pieceCount();

    String describe();

    CompoundTag save();

    double length();


    static Path segment(Vec3 from, Vec3 to) {
        return new Segment(from, to);
    }

    static Path arc(Vec3 from, Vec3 through, Vec3 to) {
        return new Arc(from, through, to);
    }

    static Path join(Path first, Path second) {
        List<Path> pieces = new ArrayList<>();
        addPieces(pieces, first);
        addPieces(pieces, second);
        return new Joined(List.copyOf(pieces));
    }

    static Path reverse(Path path) {
        if (path instanceof Segment segment) {
            return new Segment(segment.to(), segment.from());
        }
        if (path instanceof Arc arc) {
            return new Arc(arc.to(), arc.through(), arc.from());
        }
        Joined joined = (Joined) path;
        List<Path> pieces = new ArrayList<>(joined.pieces().size());
        for (int i = joined.pieces().size() - 1; i >= 0; i--) {
            pieces.add(reverse(joined.pieces().get(i)));
        }
        return new Joined(List.copyOf(pieces));
    }

    private static void addPieces(List<Path> pieces, Path path) {
        if (path instanceof Joined joined) {
            pieces.addAll(joined.pieces());
        } else {
            pieces.add(path);
        }
    }


    record Segment(Vec3 from, Vec3 to) implements Path {
        public Segment {
            requireFinite(from);
            requireFinite(to);
        }

        @Override
        public Vec3 start() {
            return from;
        }

        @Override
        public Vec3 end() {
            return to;
        }

        @Override
        public double length() {
            return from.distanceTo(to);
        }

        @Override
        public void trace(Consumer<Vec3> out, double step) {
            int steps = Math.max(1, (int) Math.ceil(length() / step));
            for (int i = 0; i <= steps; i++) {
                out.accept(from.lerp(to, i / (double) steps));
            }
        }

        @Override
        public int pieceCount() {
            return 1;
        }

        @Override
        public String describe() {
            return "line " + trim(length());
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = form("segment");
            putVec(tag, "From", from);
            putVec(tag, "To", to);
            return tag;
        }
    }

    record Arc(Vec3 from, Vec3 through, Vec3 to) implements Path {
        private static final double MIN_BEND = 1.0e-9;

        public Arc {
            requireFinite(from);
            requireFinite(through);
            requireFinite(to);
        }

        @Override
        public Vec3 start() {
            return from;
        }

        @Override
        public Vec3 end() {
            return to;
        }

        @Override
        public double length() {
            Region.Tube.Bend bend = bend();
            return bend == null ? from.distanceTo(to) : bend.length();
        }

        @Override
        public void trace(Consumer<Vec3> out, double step) {
            Region.Tube.Bend bend = bend();
            if (bend == null) {
                new Segment(from, to).trace(out, step);
                return;
            }
            int steps = Math.max(1, (int) Math.ceil(bend.length() / step));
            for (int i = 0; i <= steps; i++) {
                out.accept(i == steps ? to : bend.at(bend.sweep() * i / steps));
            }
        }

        public @Nullable Region.Tube.Bend bend() {
            Vec3 ab = through.subtract(from);
            Vec3 ac = to.subtract(from);
            Vec3 normal = ab.cross(ac);
            double normalSqr = normal.lengthSqr();
            if (normalSqr < MIN_BEND * Math.max(1.0, ab.lengthSqr() * ac.lengthSqr())) {
                return null;
            }
            Vec3 toCentre = normal.cross(ab).scale(ac.lengthSqr())
                .add(ac.cross(normal).scale(ab.lengthSqr()))
                .scale(1.0 / (2.0 * normalSqr));
            Vec3 centre = from.add(toCentre);
            Vec3 u = from.subtract(centre);
            double radius = u.length();
            u = u.scale(1.0 / radius);
            Vec3 v = normal.scale(1.0 / Math.sqrt(normalSqr)).cross(u);
            double throughAngle = angle(through.subtract(centre), u, v);
            double toAngle = angle(to.subtract(centre), u, v);
            double sweep = throughAngle <= toAngle ? toAngle : toAngle - 2.0 * Math.PI;
            return new Region.Tube.Bend(centre, u, v, radius, sweep);
        }

        private static double angle(Vec3 offset, Vec3 u, Vec3 v) {
            double angle = Math.atan2(offset.dot(v), offset.dot(u));
            return angle < 0.0 ? angle + 2.0 * Math.PI : angle;
        }

        @Override
        public int pieceCount() {
            return 1;
        }

        @Override
        public String describe() {
            return "arc " + trim(length());
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = form("arc");
            putVec(tag, "From", from);
            putVec(tag, "Through", through);
            putVec(tag, "To", to);
            return tag;
        }
    }

    record Joined(List<Path> pieces) implements Path {
        public Joined {
            if (pieces.isEmpty()) {
                throw new IllegalArgumentException("A joined path needs a piece");
            }
            if (pieces.size() > MAX_PIECES) {
                throw new IllegalArgumentException("A joined path may hold at most " + MAX_PIECES + " pieces");
            }
            for (Path piece : pieces) {
                if (piece instanceof Joined) {
                    throw new IllegalArgumentException("A joined path holds its pieces flat");
                }
            }
        }

        @Override
        public Vec3 start() {
            return pieces.get(0).start();
        }

        @Override
        public Vec3 end() {
            return pieces.get(pieces.size() - 1).end();
        }

        @Override
        public void trace(Consumer<Vec3> out, double step) {
            Vec3[] last = {null};
            for (Path piece : pieces) {
                piece.trace(point -> {
                    if (last[0] != null) {
                        if (point.equals(last[0])) {
                            return;
                        }
                        double gap = point.distanceTo(last[0]);
                        if (gap > step) {
                            int steps = (int) Math.ceil(gap / step);
                            for (int i = 1; i < steps; i++) {
                                out.accept(last[0].lerp(point, i / (double) steps));
                            }
                        }
                    }
                    out.accept(point);
                    last[0] = point;
                }, step);
            }
        }

        @Override
        public double length() {
            double length = 0.0;
            Vec3 last = null;
            for (Path piece : pieces) {
                if (last != null) {
                    length += last.distanceTo(piece.start());
                }
                length += piece.length();
                last = piece.end();
            }
            return length;
        }

        @Override
        public int pieceCount() {
            return pieces.size();
        }

        @Override
        public String describe() {
            return "joined path of " + pieces.size() + ", " + trim(length());
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = form("joined");
            ListTag list = new ListTag();
            for (Path piece : pieces) {
                list.add(piece.save());
            }
            tag.put("Pieces", list);
            return tag;
        }
    }


    static Path load(CompoundTag tag) {
        return load(tag, 0);
    }

    private static Path load(CompoundTag tag, int depth) {
        if (depth > 1) {
            throw new IllegalArgumentException("Path nested too deeply");
        }
        String form = tag.getString(TAG_FORM);
        return switch (form) {
            case "segment" -> new Segment(getVec(tag, "From"), getVec(tag, "To"));
            case "arc" -> new Arc(getVec(tag, "From"), getVec(tag, "Through"), getVec(tag, "To"));
            case "joined" -> {
                List<Path> pieces = new ArrayList<>();
                for (Tag piece : tag.getList("Pieces", Tag.TAG_COMPOUND)) {
                    pieces.add(load((CompoundTag) piece, depth + 1));
                }
                yield new Joined(List.copyOf(pieces));
            }
            default -> throw new IllegalArgumentException("Unknown path form: " + form);
        };
    }

    private static CompoundTag form(String form) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_FORM, form);
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
        if (!Double.isFinite(vec.x) || !Double.isFinite(vec.y) || !Double.isFinite(vec.z)
            || Math.abs(vec.x) > MAX_COORDINATE || Math.abs(vec.y) > MAX_COORDINATE || Math.abs(vec.z) > MAX_COORDINATE) {
            throw new IllegalArgumentException("Path coordinates must be finite and within the world");
        }
    }

    private static String trim(double value) {
        return String.format("%.1f", value);
    }
}
