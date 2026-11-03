package com.bluup.hexwright.server.path;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.hexpatterns.HexwrightConstMediaAction;
import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionIota;
import net.minecraft.core.Registry;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class PathActions {

    public static final int MAX_DIVISIONS = 1024;
    public static final double MIN_SPACING = Polyline.FINE_STEP;
    public static final double MAX_SWEEP_RADIUS = 8.0;

    private PathActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        Registry.register(registry, Hexwright.id("path_segment"),
            new ActionRegistryEntry(HexPattern.fromAngles("aawwwaa", HexDir.NORTH_WEST), SEGMENT));
        Registry.register(registry, Hexwright.id("path_arc"),
            new ActionRegistryEntry(HexPattern.fromAngles("aaqeeqaa", HexDir.NORTH_WEST), ARC));
        Registry.register(registry, Hexwright.id("path_join"),
            new ActionRegistryEntry(HexPattern.fromAngles("aaqwaawqaa", HexDir.NORTH_WEST), JOIN));
        Registry.register(registry, Hexwright.id("path_reverse"),
            new ActionRegistryEntry(HexPattern.fromAngles("ddwwdd", HexDir.NORTH_EAST), REVERSE));
        Registry.register(registry, Hexwright.id("path_point"),
            new ActionRegistryEntry(HexPattern.fromAngles("aaeaeaa", HexDir.NORTH_WEST), POINT));
        Registry.register(registry, Hexwright.id("path_length"),
            new ActionRegistryEntry(HexPattern.fromAngles("aadawadaa", HexDir.NORTH_WEST), LENGTH));
        Registry.register(registry, Hexwright.id("path_division"),
            new ActionRegistryEntry(HexPattern.fromAngles("aaqwdwdd", HexDir.NORTH_WEST), DIVISION));
    }

    public static void registerSweep() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("path_sweep"),
            new ActionRegistryEntry(HexPattern.fromAngles("aawqq", HexDir.NORTH_WEST), SWEEP));
    }

    public static Path path(List<? extends Iota> args, int idx, int argc) {
        Iota iota = args.get(idx);
        if (iota instanceof PathIota path) {
            return path.getPath();
        }
        throw MishapInvalidIota.ofType(iota, argc - idx - 1, "hexwright.path");
    }

    private static final Action SEGMENT = new PurePathAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            return List.of(new PathIota(Path.segment(vec(args, 0, getArgc()), vec(args, 1, getArgc()))));
        }
    };

    private static final Action ARC = new PurePathAction(3) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            return List.of(new PathIota(Path.arc(vec(args, 0, getArgc()), vec(args, 1, getArgc()),
                vec(args, 2, getArgc()))));
        }
    };

    private static final Action JOIN = new PurePathAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Path first = path(args, 0, getArgc());
            Path second = path(args, 1, getArgc());
            if (first.pieceCount() + second.pieceCount() > Path.MAX_PIECES) {
                throw MishapInvalidIota.of(args.get(0), getArgc() - 1, "hexwright.path_too_complex", Path.MAX_PIECES);
            }
            return List.of(new PathIota(Path.join(first, second)));
        }
    };

    private static final Action REVERSE = new PurePathAction(1) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            return List.of(new PathIota(Path.reverse(path(args, 0, getArgc()))));
        }
    };

    private static final Action POINT = new PurePathAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Path path = path(args, 0, getArgc());
            double fraction = OperatorUtils.getDoubleBetween(args, 1, 0.0, 1.0, getArgc());
            Polyline line = Polyline.of(path);
            return List.of(new Vec3Iota(line.at(fraction * line.length())));
        }
    };

    private static final Action LENGTH = new PurePathAction(1) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            return List.of(new DoubleIota(path(args, 0, getArgc()).length()));
        }
    };

    private static final Action DIVISION = new PurePathAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Path path = path(args, 0, getArgc());
            double spacing = OperatorUtils.getDouble(args, 1, getArgc());
            if (!(spacing >= MIN_SPACING) || !Double.isFinite(spacing)) {
                throw MishapInvalidIota.of(args.get(1), 0, "hexwright.path_spacing", MIN_SPACING);
            }
            double length = path.length();
            int intervals = (int) Math.ceil(length / spacing - 1.0e-9);
            if (intervals + 1 > MAX_DIVISIONS) {
                throw MishapInvalidIota.of(args.get(1), 0, "hexwright.path_divisions", MAX_DIVISIONS);
            }
            Polyline line = Polyline.of(path);
            List<Iota> points = new ArrayList<>(intervals + 1);
            for (int i = 0; i <= intervals; i++) {
                points.add(new Vec3Iota(line.at(Math.min(length, i * spacing))));
            }
            return List.of(new ListIota(points));
        }
    };

    private static final Action SWEEP = new PurePathAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Path path = path(args, 0, getArgc());
            double radius = OperatorUtils.getDoubleBetween(args, 1, 0.0, MAX_SWEEP_RADIUS, getArgc());
            return List.of(new RegionIota(sweep(path, radius)));
        }
    };

    public static Region sweep(Path path, double radius) {
        List<Path> parts = path instanceof Path.Joined joined ? joined.pieces() : List.of(path);
        List<Region.Tube.Piece> pieces = new ArrayList<>(parts.size() * 2);
        Vec3 last = null;
        for (Path part : parts) {
            if (last != null && !last.equals(part.start())) {
                pieces.add(new Region.Tube.Straight(last, part.start()));
            }
            Region.Tube.Bend bend = part instanceof Path.Arc arc ? arc.bend() : null;
            pieces.add(bend != null ? bend : new Region.Tube.Straight(part.start(), part.end()));
            last = part.end();
        }
        return Region.tube(pieces, radius);
    }

    private static Vec3 vec(List<? extends Iota> args, int idx, int argc) {
        Vec3 vec = OperatorUtils.getVec3(args, idx, argc);
        if (!Double.isFinite(vec.x) || !Double.isFinite(vec.y) || !Double.isFinite(vec.z)
            || Math.abs(vec.x) > Path.MAX_COORDINATE || Math.abs(vec.y) > Path.MAX_COORDINATE
            || Math.abs(vec.z) > Path.MAX_COORDINATE) {
            throw MishapInvalidIota.of(args.get(idx), argc - idx - 1, "hexwright.path_coordinate");
        }
        return vec;
    }

    private abstract static class PurePathAction extends HexwrightConstMediaAction {

        private final int argc;

        PurePathAction(int argc) {
            this.argc = argc;
        }

        @Override
        public int getArgc() {
            return argc;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }
    }
}
