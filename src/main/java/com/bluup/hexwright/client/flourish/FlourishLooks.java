package com.bluup.hexwright.client.flourish;

import at.petrak.hexcasting.common.particles.ConjureParticleOptions;
import com.bluup.hexwright.server.flourish.FlourishCast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntUnaryOperator;

final class FlourishLooks {

    private FlourishLooks() {
    }

    static FlourishLook create(FlourishCast cast, ClientLevel level) {
        Entity caster = level.getEntity(cast.casterId());
        Vec3 centre = clearOfBlocks(level, cast.pos(), caster);
        double extent = cast.extent();
        Vec3 facing = cast.axis().lengthSqr() > 0.5 ? cast.axis() : toward(centre, caster);
        return switch (cast.flourish()) {
            case HEXAGRAM -> new Hexagram(floorBelow(level, centre, extent), Mth.clamp(extent * 1.8, 1.0, 8.0));
            case LATTICE -> {
                double radius = Mth.clamp(extent * 2.0, 1.2, 7.0);
                yield new Lattice(standClear(level, centre, radius), facing, radius);
            }
            case NOVA -> new Nova(centre, cast.axis(), extent);
            case THREAD -> new ThreadLook(centre, caster, extent);
            case SIGIL -> new Sigil(centre.add(0.0, extent * 0.5 + 0.9, 0.0), Mth.clamp(extent * 1.1, 0.6, 3.0),
                sigilPattern(new Random(cast.seed())));
            case PILLAR -> new Pillar(floorBelow(level, centre, extent), extent);
            case ORRERY -> new Orrery(centre, Mth.clamp(extent * 1.3, 0.8, 5.0), cast.seed());
            case BLOSSOM -> new Blossom(centre, Mth.clamp(extent * 1.8, 1.0, 6.5));
            case AEGIS -> new Aegis(centre, Mth.clamp(extent * 1.5, 0.9, 7.0));
            case MAELSTROM -> new Maelstrom(floorBelow(level, centre, extent), Mth.clamp(extent * 2.0, 1.2, 8.0));
            case WATCHER -> new Watcher(centre.add(0.0, extent * 0.4 + 0.8, 0.0), Mth.clamp(extent * 1.4, 0.8, 3.5));
            case CROWN -> new Crown(centre, Mth.clamp(extent * 1.2, 0.7, 5.0));
            case STARFALL -> Starfall.of(floorBelow(level, centre, extent), extent, new Random(cast.seed()));
            case TEMPEST -> Tempest.of(centre, extent, new Random(cast.seed()));
            case ECLIPSE -> new Eclipse(centre, Mth.clamp(extent * 1.6, 1.0, 5.0));
            case TIDE -> new Tide(floorBelow(level, centre, extent), Mth.clamp(extent * 2.2, 1.2, 8.0));
            case GEODE -> Geode.of(centre, extent, new Random(cast.seed()));
            case CLOCKWORK -> {
                double radius = Mth.clamp(extent * 1.6, 1.0, 6.0);
                yield new Clockwork(standClear(level, centre, radius), facing, radius);
            }
            case PYRE -> new Pyre(floorBelow(level, centre, extent), Mth.clamp(extent * 1.1, 0.6, 5.0),
                Mth.clamp(0.45 + extent * 0.35, 0.6, 2.0));
            case SERAPH -> new Seraph(centre.add(0.0, 0.3, 0.0), Mth.clamp(extent * 1.8, 1.2, 6.0));
        };
    }

    private record Hexagram(Vec3 floor, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 32;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            for (int i = 0; i < 10; i++) {
                double a = i * Mth.TWO_PI / 10.0;
                mote(level, colour.applyAsInt(i & 3),
                    floor.add(Math.cos(a) * radius * 0.96, 0.05, Math.sin(a) * radius * 0.96), 0.0, 0.04, 0.0);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.disc(FlourishPen.SEAL, floor, FlourishPen.UP, radius, progress);
        }
    }

    private record Lattice(Vec3 centre, Vec3 facing, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 26;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.disc(FlourishPen.LATTICE, centre, facing, radius, progress);
        }
    }

    private record Nova(Vec3 centre, Vec3 axis, double extent) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 18;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            Random random = new Random();
            for (int i = 0; i < 16; i++) {
                Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                    .normalize().scale(0.15);
                mote(level, colour.applyAsInt(i & 3), centre, out.x, out.y, out.z);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.billboard(FlourishPen.STAR, centre, Mth.clamp(extent * 1.8, 1.0, 6.0), progress);
            Vec3 ringNormal = axis.lengthSqr() > 0.5 ? axis : FlourishPen.UP;
            pen.disc(FlourishPen.RING, centre, ringNormal, Mth.clamp(extent * 2.6, 1.3, 8.0),
                Math.min(1.0f, progress * 1.15f));
        }
    }

    private record ThreadLook(Vec3 centre, @Nullable Entity caster, double extent) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 22;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            Vec3 hand = caster != null && caster.isAlive() ? handOf(caster, pen.partialTick()) : null;
            double ring = Mth.clamp(extent * 1.2, 0.6, 4.0);
            if (hand == null || hand.distanceToSqr(centre) < 1.5 * 1.5) {
                pen.disc(FlourishPen.RING, centre, toward(centre, caster), ring, progress);
                pen.billboard(FlourishPen.STAR, centre, ring * 0.6, progress);
                return;
            }
            pen.axial(FlourishPen.THREAD, hand, centre, 0.3, 0.45, 0.1, progress);
            pen.disc(FlourishPen.RING, centre, hand.subtract(centre), ring,
                Mth.clamp((progress - 0.22f) / 0.78f, 0.0f, 1.0f));
        }
    }

    private record Sigil(Vec3 centre, double size, List<float[]> points) implements FlourishLook {
        private static final double STROKE = 0.035;

        @Override
        public int lifetimeTicks() {
            return 40;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            Vec3 origin = centre.add(0.0, progress * 0.4, 0.0);
            Vec3 right = pen.cameraRight().scale(size);
            Vec3 up = pen.cameraUp().scale(size);
            int segments = points.size() - 1;
            for (int i = 0; i < segments; i++) {
                float[] a = points.get(i);
                float[] b = points.get(i + 1);
                float start = i / (float) segments;
                float end = (i + 1) / (float) segments;
                pen.segment(start, end, start, end).axial(FlourishPen.STROKE,
                    origin.add(right.scale(a[0])).add(up.scale(a[1])),
                    origin.add(right.scale(b[0])).add(up.scale(b[1])),
                    STROKE, STROKE * 7.0, STROKE * 7.0, progress);
            }
        }
    }

    private record Pillar(Vec3 floor, double extent) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 28;
        }

        private double width() {
            return Mth.clamp(extent * 0.6, 0.35, 2.0);
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            Random random = new Random();
            double spread = width() * 0.5;
            for (int i = 0; i < 8; i++) {
                mote(level, colour.applyAsInt(i & 3),
                    floor.add((random.nextDouble() - 0.5) * spread, 0.1, (random.nextDouble() - 0.5) * spread),
                    0.0, 0.12 + random.nextDouble() * 0.08, 0.0);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            double width = width();
            double height = Mth.clamp(2.5 + extent * 1.5, 2.5, 10.0);
            pen.axial(FlourishPen.BEAM, floor, floor.add(0.0, height, 0.0), width, width * 2.2, 0.0, progress);
            pen.disc(FlourishPen.RING, floor, FlourishPen.UP, width * 3.2, progress);
        }
    }

    private record Orrery(Vec3 centre, double radius, int seed) implements FlourishLook {
        private static final double[] TILTS = {0.35, 0.9, 1.25};
        private static final float[] SPEEDS = {2.6f, -3.4f, 4.2f};
        private static final double[] SIZES = {1.0, 0.78, 0.58};

        @Override
        public int lifetimeTicks() {
            return 36;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            double turn = (seed & 0xFFFF) / 65535.0 * Mth.TWO_PI;
            for (int i = 0; i < 3; i++) {
                double azimuth = turn + i * 2.1;
                Vec3 normal = new Vec3(Math.sin(TILTS[i]) * Math.cos(azimuth), Math.cos(TILTS[i]),
                    Math.sin(TILTS[i]) * Math.sin(azimuth));
                pen.segment(SPEEDS[i], i / 3.0f, 0.0f, 0.0f)
                    .disc(FlourishPen.ORBIT, centre, normal, radius * SIZES[i], progress);
            }
            pen.billboard(FlourishPen.STAR, centre, radius * 0.35, progress);
        }
    }

    private record Blossom(Vec3 centre, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 30;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            for (int i = 0; i < 6; i++) {
                double a = i * Mth.TWO_PI / 6.0;
                mote(level, colour.applyAsInt(i & 3),
                    centre.add(Math.cos(a) * radius * 0.9, 0.0, Math.sin(a) * radius * 0.9), 0.0, 0.03, 0.0);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.disc(FlourishPen.PETALS, centre, FlourishPen.UP, radius, progress);
        }
    }

    private record Aegis(Vec3 centre, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 30;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.billboard(FlourishPen.SHELL, centre, radius, progress);
        }
    }

    private record Maelstrom(Vec3 floor, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 28;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            for (int i = 0; i < 14; i++) {
                double a = i * Mth.TWO_PI / 14.0;
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                mote(level, colour.applyAsInt(i & 3), floor.add(cos * radius * 0.9, 0.1, sin * radius * 0.9),
                    -sin * 0.12 - cos * 0.06, 0.0, cos * 0.12 - sin * 0.06);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.disc(FlourishPen.VORTEX, floor, FlourishPen.UP, radius, progress);
        }
    }

    private record Watcher(Vec3 centre, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 34;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.billboard(FlourishPen.EYE, centre, radius, progress);
        }
    }

    private record Crown(Vec3 centre, double radius) implements FlourishLook {
        private static final double HALF_HEIGHT = 0.22;

        @Override
        public int lifetimeTicks() {
            return 36;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            for (int i = 0; i < 8; i++) {
                double a = i * Mth.TWO_PI / 8.0;
                mote(level, colour.applyAsInt(i & 3),
                    centre.add(Math.cos(a) * radius, HALF_HEIGHT, Math.sin(a) * radius), 0.0, 0.05, 0.0);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.band(FlourishPen.BAND, centre, radius, HALF_HEIGHT, progress);
        }
    }

    private record Starfall(Vec3 floor, double spread, List<Vec3[]> stars, float[] delays) implements FlourishLook {
        private static final int STARS = 5;
        private static final float FLIGHT = 0.28f;

        static Starfall of(Vec3 floor, double extent, Random random) {
            double spread = Mth.clamp(extent * 1.5, 0.8, 6.0);
            double slant = random.nextDouble() * Mth.TWO_PI;
            List<Vec3[]> stars = new ArrayList<>();
            float[] delays = new float[STARS];
            for (int i = 0; i < STARS; i++) {
                Vec3 end = floor.add((random.nextDouble() - 0.5) * spread, 0.05, (random.nextDouble() - 0.5) * spread);
                double lean = 1.5 + random.nextDouble();
                double drift = slant + (random.nextDouble() - 0.5) * 0.4;
                Vec3 start = end.add(Math.cos(drift) * lean, 7.0 + random.nextDouble() * 2.0, Math.sin(drift) * lean);
                stars.add(new Vec3[]{start, end});
                delays[i] = i * 0.07f + random.nextFloat() * 0.05f;
            }
            return new Starfall(floor, spread, stars, delays);
        }

        @Override
        public int lifetimeTicks() {
            return 40;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            float landed = 0.0f;
            for (int i = 0; i < STARS; i++) {
                Vec3[] star = stars.get(i);
                pen.segment(delays[i], FLIGHT, 0.0f, 0.0f)
                    .axial(FlourishPen.STREAK, star[0], star[1], 0.05, 0.35, 0.4, progress);
                landed = Math.max(landed, delays[i] + FLIGHT);
            }
            if (progress > landed) {
                pen.disc(FlourishPen.RING, floor, FlourishPen.UP, spread * 1.3, (progress - landed) / (1.0f - landed));
            }
        }
    }

    private record Tempest(Vec3 centre, double extent, List<Vec3> ends) implements FlourishLook {
        private static final int ARCS = 6;

        static Tempest of(Vec3 centre, double extent, Random random) {
            double reach = Mth.clamp(extent * 2.0, 1.2, 7.0);
            List<Vec3> ends = new ArrayList<>();
            for (int i = 0; i < ARCS; i++) {
                double a = (i + random.nextDouble() * 0.6) * Mth.TWO_PI / ARCS;
                double y = -0.6 + random.nextDouble() * 1.1;
                Vec3 out = new Vec3(Math.cos(a), y, Math.sin(a)).normalize();
                ends.add(centre.add(out.scale(reach * (0.6 + random.nextDouble() * 0.4))));
            }
            return new Tempest(centre, extent, ends);
        }

        @Override
        public int lifetimeTicks() {
            return 20;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            for (int i = 0; i < ends.size(); i++) {
                mote(level, colour.applyAsInt(i & 3), ends.get(i), 0.0, 0.02, 0.0);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            for (int i = 0; i < ends.size(); i++) {
                pen.segment(i * 0.137f, 0.0f, 0.0f, 0.0f)
                    .axial(FlourishPen.ARC, centre, ends.get(i), 0.025, 0.8, 0.05, progress);
            }
            pen.segment(0.91f, 0.0f, 0.0f, 0.0f)
                .axial(FlourishPen.ARC, centre.add(0.0, 5.0, 0.0), centre, 0.03, 0.8, 0.05, progress);
            pen.billboard(FlourishPen.STAR, centre, Mth.clamp(extent * 1.2, 0.7, 3.0), progress);
        }
    }

    private record Eclipse(Vec3 centre, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 36;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.billboard(FlourishPen.ECLIPSE, centre, radius, progress);
        }
    }

    private record Tide(Vec3 floor, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 30;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            Random random = new Random();
            for (int i = 0; i < 6; i++) {
                mote(level, colour.applyAsInt(i & 3), floor.add(0.0, 0.1, 0.0),
                    (random.nextDouble() - 0.5) * 0.08, 0.12 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.08);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.disc(FlourishPen.RIPPLE, floor, FlourishPen.UP, radius, progress);
        }
    }

    private record Geode(Vec3 centre, double extent, List<Vec3> directions, double[] reaches, float[] spins)
        implements FlourishLook {
        private static final int SHARDS = 9;

        static Geode of(Vec3 centre, double extent, Random random) {
            double reach = Mth.clamp(extent * 1.6, 0.8, 5.0);
            List<Vec3> directions = new ArrayList<>();
            double[] reaches = new double[SHARDS];
            float[] spins = new float[SHARDS];
            for (int i = 0; i < SHARDS; i++) {
                directions.add(new Vec3(random.nextGaussian(), random.nextGaussian() + 0.4, random.nextGaussian())
                    .normalize());
                reaches[i] = reach * (0.6 + random.nextDouble() * 0.4);
                spins[i] = (random.nextFloat() - 0.5f) * 3.0f;
            }
            return new Geode(centre, extent, directions, reaches, spins);
        }

        @Override
        public int lifetimeTicks() {
            return 28;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            for (int i = 0; i < directions.size(); i++) {
                Vec3 out = directions.get(i).scale(0.12);
                mote(level, colour.applyAsInt(i & 3), centre, out.x, out.y, out.z);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            double flown = 1.0 - Math.pow(1.0 - Math.min(progress / 0.5, 1.0), 3.0);
            double size = Mth.clamp(extent * 0.35, 0.18, 0.7);
            for (int i = 0; i < directions.size(); i++) {
                Vec3 direction = directions.get(i);
                Vec3 at = centre.add(direction.scale(reaches[i] * flown)).add(0.0, -0.8 * progress * progress, 0.0);
                double heading = Math.atan2(direction.dot(pen.cameraUp()), direction.dot(pen.cameraRight()));
                pen.segment((float) (Math.PI / 2.0 - heading) + spins[i] * progress, i / 9.0f, 0.0f, 0.0f)
                    .billboard(FlourishPen.SHARD, at, size, progress);
            }
            pen.billboard(FlourishPen.STAR, centre, Mth.clamp(extent * 1.2, 0.6, 3.0), Math.min(1.0f, progress * 1.6f));
        }
    }

    private record Clockwork(Vec3 centre, Vec3 facing, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 34;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.disc(FlourishPen.GEARS, centre, facing, radius, progress);
        }
    }

    private record Pyre(Vec3 floor, double radius, double halfHeight) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 34;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            Random random = new Random();
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Mth.TWO_PI;
                mote(level, colour.applyAsInt(i & 3), floor.add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius),
                    0.0, 0.08 + random.nextDouble() * 0.06, 0.0);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.band(FlourishPen.FLAME, floor.add(0.0, halfHeight, 0.0), radius, halfHeight, progress);
            pen.disc(FlourishPen.RING, floor, FlourishPen.UP, radius * 1.15, progress);
        }
    }

    private record Seraph(Vec3 centre, double radius) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 40;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            Random random = new Random();
            for (int i = 0; i < 6; i++) {
                mote(level, colour.applyAsInt(i & 3),
                    centre.add((random.nextDouble() - 0.5) * radius * 1.2, radius * 0.5, (random.nextDouble() - 0.5) * radius * 1.2),
                    0.0, -0.02, 0.0);
            }
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.billboard(FlourishPen.WINGS, centre, radius, progress);
        }
    }

    static List<float[]> sigilPattern(Random random) {
        int[][] steps = {{1, -1}, {1, 0}, {0, 1}, {-1, 1}, {-1, 0}, {0, -1}};
        int[] turns = {0, 1, 5, 0, 1, 5, 2, 4};
        int segments = 5 + random.nextInt(4);
        int dir = random.nextInt(6);
        int q = 0;
        int r = 0;
        Set<Long> edges = new HashSet<>();
        List<int[]> cells = new ArrayList<>();
        cells.add(new int[]{0, 0});
        for (int i = 0; i < segments; i++) {
            boolean moved = false;
            for (int attempt = 0; attempt < 8 && !moved; attempt++) {
                int next = i == 0 ? dir : Math.floorMod(dir + turns[random.nextInt(turns.length)], 6);
                int nq = q + steps[next][0];
                int nr = r + steps[next][1];
                long edge = edgeKey(q, r, nq, nr);
                if (edges.add(edge)) {
                    dir = next;
                    q = nq;
                    r = nr;
                    cells.add(new int[]{q, r});
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }

        List<float[]> points = new ArrayList<>();
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int[] cell : cells) {
            float x = cell[0] + cell[1] * 0.5f;
            float y = -cell[1] * 0.8660254f;
            points.add(new float[]{x, y});
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float reach = 1e-3f;
        for (float[] p : points) {
            p[0] -= cx;
            p[1] -= cy;
            reach = Math.max(reach, (float) Math.sqrt(p[0] * p[0] + p[1] * p[1]));
        }
        for (float[] p : points) {
            p[0] /= reach;
            p[1] /= reach;
        }
        return points;
    }

    private static long edgeKey(int q0, int r0, int q1, int r1) {
        long a = ((long) q0 << 16) ^ (r0 & 0xFFFF);
        long b = ((long) q1 << 16) ^ (r1 & 0xFFFF);
        return a < b ? (a << 32) ^ (b & 0xFFFFFFFFL) : (b << 32) ^ (a & 0xFFFFFFFFL);
    }

    private static void mote(ClientLevel level, int colour, Vec3 at, double vx, double vy, double vz) {
        level.addParticle(new ConjureParticleOptions(colour), at.x, at.y, at.z, vx, vy, vz);
    }

    private static Vec3 handOf(Entity caster, float partialTick) {
        Vec3 eye = caster.getEyePosition(partialTick);
        Vec3 look = caster.getViewVector(partialTick);
        Vec3 right = look.cross(FlourishPen.UP);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        boolean rightHanded = !(caster instanceof Player player) || player.getMainArm() == HumanoidArm.RIGHT;
        return eye.add(look.scale(0.45)).add(right.scale(rightHanded ? 0.32 : -0.32)).add(0.0, -0.38, 0.0);
    }

    private static Vec3 toward(Vec3 centre, @Nullable Entity caster) {
        Vec3 target = caster != null
            ? caster.getEyePosition()
            : Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 direction = target.subtract(centre);
        return direction.lengthSqr() < 1e-6 ? FlourishPen.UP : direction.normalize();
    }

    private static Vec3 clearOfBlocks(ClientLevel level, Vec3 pos, @Nullable Entity caster) {
        if (!solidAt(level, pos)) {
            return pos;
        }
        Vec3 direction = toward(pos, caster);
        for (int i = 1; i <= 6; i++) {
            Vec3 step = pos.add(direction.scale(i * 0.25));
            if (!solidAt(level, step)) {
                return step;
            }
        }
        return pos;
    }

    private static boolean solidAt(ClientLevel level, Vec3 pos) {
        BlockPos block = BlockPos.containing(pos);
        VoxelShape shape = level.getBlockState(block).getCollisionShape(level, block);
        return !shape.isEmpty() && shape.bounds().move(block).contains(pos);
    }

    private static Vec3 standClear(ClientLevel level, Vec3 centre, double radius) {
        Vec3 ground = groundBelow(level, centre, radius);
        return ground != null ? new Vec3(centre.x, ground.y + radius, centre.z) : centre;
    }

    private static Vec3 floorBelow(ClientLevel level, Vec3 centre, double extent) {
        Vec3 ground = groundBelow(level, centre, extent + 2.5);
        return ground != null ? ground : centre.subtract(0.0, extent * 0.6, 0.0);
    }

    private static @Nullable Vec3 groundBelow(ClientLevel level, Vec3 centre, double depth) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        BlockHitResult hit = level.clip(new ClipContext(centre, centre.subtract(0.0, depth, 0.0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? new Vec3(centre.x, hit.getLocation().y + 0.04, centre.z) : null;
    }
}
