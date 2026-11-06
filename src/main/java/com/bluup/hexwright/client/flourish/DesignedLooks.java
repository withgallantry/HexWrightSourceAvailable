package com.bluup.hexwright.client.flourish;

import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.common.flourish.FlourishFigure;
import com.bluup.hexwright.common.flourish.FlourishMotif;
import com.bluup.hexwright.server.flourish.FlourishCast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;

final class DesignedLooks {

    private static final int SCATTERED = 7;
    private static final int FALLING = 5;
    private static final int BURSTING = 6;

    static final int MOTE_LAYERS = 2;

    private DesignedLooks() {
    }

    interface Stage {
        Vec3 centre();

        double extent();

        Vec3 facing();

        Vec3 floor();

        Vec3 standing(double radius);

        @Nullable
        Vec3 hand(float partialTick);

        int seed();
    }

    static Stage inWorld(FlourishCast cast, ClientLevel level) {
        Entity caster = level.getEntity(cast.casterId());
        Vec3 centre = FlourishLooks.clearOfBlocks(level, cast.pos(), caster);
        double extent = cast.extent();
        Vec3 facing = cast.axis().lengthSqr() > 0.5 ? cast.axis() : FlourishLooks.toward(centre, caster);
        return new WorldStage(level, caster, centre, extent, facing, FlourishLooks.floorBelow(level, centre, extent),
            cast.seed());
    }

    private record WorldStage(ClientLevel level, @Nullable Entity caster, Vec3 centre, double extent, Vec3 facing,
                              Vec3 floor, int seed) implements Stage {
        @Override
        public Vec3 standing(double radius) {
            return FlourishLooks.standClear(level, centre, radius);
        }

        @Override
        public @Nullable Vec3 hand(float partialTick) {
            return caster != null && caster.isAlive() ? FlourishLooks.handOf(caster, partialTick) : null;
        }
    }

    static FlourishLook create(FlourishDesign design, Stage stage) {
        Random random = new Random(stage.seed());
        List<Timed> layers = new ArrayList<>();
        int lifetime = 1;
        for (int i = 0; i < design.layers().size(); i++) {
            FlourishDesign.Layer layer = design.layers().get(i);
            int duration = duration(layer);
            layers.add(new Timed(layer.delayTicks(), duration, layer.shade(), part(layer, i, stage, random)));
            lifetime = Math.max(lifetime, layer.delayTicks() + duration);
        }
        return new Designed(List.copyOf(layers), lifetime, design.motes(), stage);
    }

    static int style(FlourishMotif motif) {
        return switch (motif) {
            case SEAL -> FlourishPen.SEAL;
            case LATTICE -> FlourishPen.LATTICE;
            case RING -> FlourishPen.RING;
            case RIPPLE -> FlourishPen.RIPPLE;
            case ORBIT -> FlourishPen.ORBIT;
            case PETALS -> FlourishPen.PETALS;
            case GEARS -> FlourishPen.GEARS;
            case VORTEX -> FlourishPen.VORTEX;
            case STAR -> FlourishPen.STAR;
            case EYE -> FlourishPen.EYE;
            case SHARD -> FlourishPen.SHARD;
            case SIGIL -> FlourishPen.STROKE;
            case WARD -> FlourishPen.SHELL;
            case DARK_SUN -> FlourishPen.ECLIPSE;
            case WINGS -> FlourishPen.WINGS;
            case THREAD -> FlourishPen.THREAD;
            case BEAM -> FlourishPen.BEAM;
            case STREAK -> FlourishPen.STREAK;
            case LIGHTNING -> FlourishPen.ARC;
            case GLYPHS -> FlourishPen.BAND;
            case FLAME -> FlourishPen.FLAME;
            case FIGURE -> FlourishPen.FIGURE;
            case WEB -> FlourishPen.WEB;
            case FROST -> FlourishPen.FROST;
            case MAZE -> FlourishPen.MAZE;
            case SERPENT -> FlourishPen.SERPENT;
            case MOTH -> FlourishPen.MOTH;
            case CONSTELLATION -> FlourishPen.CONSTELLATION;
            case TESSERACT -> FlourishPen.TESSERACT;
            case RIFT -> FlourishPen.RIFT;
            case BLADE -> FlourishPen.BLADE;
            case CHAINS -> FlourishPen.CHAINS;
            case SINGULARITY -> FlourishPen.SINGULARITY;
            case EFFIGY -> FlourishPen.EFFIGY;
            case AURORA -> FlourishPen.AURORA;
            case PRISM -> FlourishPen.PRISM;
            case ROSE_WINDOW -> FlourishPen.ROSE_WINDOW;
            case WILDFLOWER -> FlourishPen.WILDFLOWER;
            case DANDELION -> FlourishPen.DANDELION;
            case MYCELIUM -> FlourishPen.MYCELIUM;
            case BRIAR -> FlourishPen.BRIAR;
            case WORLD_TREE -> FlourishPen.WORLD_TREE;
        };
    }

    static int duration(FlourishDesign.Layer layer) {
        return layer.figure() != null ? layer.figure().lifetimeTicks() : duration(layer.motif());
    }

    static int duration(FlourishMotif motif) {
        return switch (motif) {
            case FIGURE -> FlourishFigure.DEFAULT.lifetimeTicks();
            case STAR -> 18;
            case LIGHTNING -> 20;
            case RING, THREAD -> 22;
            case LATTICE -> 26;
            case VORTEX, SHARD, BEAM -> 28;
            case RIPPLE, PETALS, WARD -> 30;
            case SEAL -> 32;
            case GEARS, EYE, FLAME, WEB -> 34;
            case ORBIT, DARK_SUN, GLYPHS, CHAINS, RIFT, PRISM -> 36;
            case SIGIL, WINGS, STREAK, FROST, MOTH, BLADE, TESSERACT, MYCELIUM -> 40;
            case CONSTELLATION, SERPENT, SINGULARITY, EFFIGY, ROSE_WINDOW, DANDELION, BRIAR -> 44;
            case AURORA, WILDFLOWER -> 48;
            case WORLD_TREE -> 52;
            case MAZE -> 46;
        };
    }

    private interface Part {
        void draw(FlourishPen pen, float progress);
    }

    private record Timed(int delay, int duration, int shade, Part part) {
    }

    private record Designed(List<Timed> layers, int lifetimeTicks, FlourishDesign.Motes motes, Stage stage)
        implements FlourishLook {

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            throwMotes(motes, stage, level, colour);
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            float ticks = progress * lifetimeTicks;
            for (Timed layer : layers) {
                float local = (ticks - layer.delay()) / layer.duration();
                if (local < 0.0f || local >= 1.0f) {
                    continue;
                }
                pen.shade(layer.shade());
                layer.part().draw(pen, local);
            }
            pen.shade(0);
        }
    }

    private static Part part(FlourishDesign.Layer layer, int index, Stage stage, Random random) {
        return switch (layer.placement().kind()) {
            case DISC -> disc(layer, index, stage);
            case FACING -> facing(layer, stage, random);
            case AXIAL -> axial(layer, stage, random);
            case BAND -> band(layer, stage);
        };
    }

    private static Part disc(FlourishDesign.Layer layer, int index, Stage stage) {
        double extent = stage.extent();
        double radius = Mth.clamp(extent * 1.8, 1.0, 7.0) * layer.scale();
        int style = style(layer.motif());
        Vec3 centre = switch (layer.placement()) {
            case GROUND -> stage.floor();
            case STANDING -> stage.standing(radius);
            case OVERHEAD -> stage.centre().add(0.0, extent * 0.5 + 1.2, 0.0);
            default -> stage.centre();
        };
        Vec3 normal = layer.placement() == FlourishMotif.Placement.STANDING ? stage.facing() : FlourishPen.UP;
        if (layer.motif() == FlourishMotif.ORBIT) {
            float speed = index % 2 == 0 ? 2.8f : -3.4f;
            float colour = index / (float) FlourishDesign.MAX_LAYERS;
            return (pen, progress) -> pen.segment(speed, colour, 0.0f, 0.0f).disc(style, centre, normal, radius, progress);
        }
        return (pen, progress) -> prepared(pen, layer).disc(style, centre, normal, radius, progress);
    }

    private static FlourishPen prepared(FlourishPen pen, FlourishDesign.Layer layer) {
        return layer.figure() != null ? pen.figure(layer.figure()) : pen;
    }

    private static Part facing(FlourishDesign.Layer layer, Stage stage, Random random) {
        double extent = stage.extent();
        FlourishMotif motif = layer.motif();
        if (layer.placement() == FlourishMotif.Placement.SCATTER) {
            return scatter(layer, stage, random);
        }
        double radius = Mth.clamp(extent * 1.4, 0.8, 4.0) * layer.scale();
        Vec3 anchor = layer.placement() == FlourishMotif.Placement.ABOVE
            ? stage.centre().add(0.0, extent * 0.4 + 0.8, 0.0)
            : stage.centre();
        if (motif == FlourishMotif.SIGIL) {
            List<float[]> points = FlourishLooks.sigilPattern(random);
            return (pen, progress) -> FlourishLooks.sigil(pen, points, anchor.add(0.0, progress * 0.4, 0.0),
                radius * 0.8, progress);
        }
        int style = style(motif);
        return (pen, progress) -> prepared(pen, layer).billboard(style, anchor, radius, progress);
    }

    private static Part scatter(FlourishDesign.Layer layer, Stage stage, Random random) {
        FlourishMotif motif = layer.motif();
        double scale = layer.scale();
        double extent = stage.extent();
        double reach = Mth.clamp(extent * 1.6, 0.8, 5.0) * scale;
        double size = Mth.clamp(extent * 0.35, 0.18, 0.7) * scale;
        Vec3 centre = stage.centre();
        Vec3[] directions = new Vec3[SCATTERED];
        double[] reaches = new double[SCATTERED];
        float[] spins = new float[SCATTERED];
        List<List<float[]>> sigils = new ArrayList<>();
        for (int i = 0; i < SCATTERED; i++) {
            directions[i] = new Vec3(random.nextGaussian(), random.nextGaussian() + 0.4, random.nextGaussian()).normalize();
            reaches[i] = reach * (0.6 + random.nextDouble() * 0.4);
            spins[i] = (random.nextFloat() - 0.5f) * 3.0f;
            if (motif == FlourishMotif.SIGIL) {
                sigils.add(FlourishLooks.sigilPattern(random));
            }
        }
        int style = style(motif);
        double drop = motif == FlourishMotif.MOTH ? -0.8 : 0.8;
        return (pen, progress) -> {
            double flown = 1.0 - Math.pow(1.0 - Math.min(progress / 0.5, 1.0), 3.0);
            for (int i = 0; i < SCATTERED; i++) {
                Vec3 direction = directions[i];
                Vec3 at = centre.add(direction.scale(reaches[i] * flown)).add(0.0, -drop * progress * progress, 0.0);
                if (motif == FlourishMotif.SIGIL) {
                    FlourishLooks.sigil(pen, sigils.get(i), at, size, progress);
                    continue;
                }
                if (motif == FlourishMotif.SHARD) {
                    double heading = Math.atan2(direction.dot(pen.cameraUp()), direction.dot(pen.cameraRight()));
                    pen.segment((float) (Math.PI / 2.0 - heading) + spins[i] * progress, i / (float) SCATTERED, 0.0f, 0.0f);
                } else if (motif == FlourishMotif.MOTH) {
                    pen.segment(spins[i] * 0.2f, i / (float) SCATTERED, 0.0f, 0.0f);
                }
                prepared(pen, layer).billboard(style, at, size, progress);
            }
        };
    }

    private record Strand(double width, double halfBreadth, double margin) {
    }

    private static Strand strand(FlourishMotif motif, double extent, double scale) {
        return switch (motif) {
            case THREAD -> new Strand(0.3 * scale, 0.45 * scale, 0.1);
            case BEAM -> {
                double width = Mth.clamp(extent * 0.6, 0.35, 2.0) * scale;
                yield new Strand(width, width * 2.2, 0.0);
            }
            case STREAK -> new Strand(0.05 * scale, 0.35 * scale, 0.4);
            case BLADE -> new Strand(1.1 * scale, 0.25 * scale, 0.25 * scale);
            default -> new Strand(0.025 * Math.sqrt(scale), 0.8 * scale, 0.05);
        };
    }

    private static void segment(FlourishPen pen, FlourishMotif motif, int index, int count, float delay) {
        if (motif == FlourishMotif.STREAK || motif == FlourishMotif.BLADE) {
            pen.segment(delay, count > 1 ? 0.28f : 0.45f, 0.0f, 0.0f);
        } else if (motif == FlourishMotif.LIGHTNING) {
            pen.segment(index * 0.137f, 0.0f, 0.0f, 0.0f);
        }
    }

    private static Part axial(FlourishDesign.Layer layer, Stage stage, Random random) {
        FlourishMotif motif = layer.motif();
        double extent = stage.extent();
        double scale = layer.scale();
        if (motif.grows()) {
            int style = style(motif);
            Vec3 floor = stage.floor();
            double height = FlourishLooks.plantHeight(style, extent) * scale;
            return (pen, progress) -> FlourishLooks.plant(pen, style, floor, height, progress);
        }
        Strand strand = strand(motif, extent, scale);
        int style = style(motif);
        return switch (layer.placement()) {
            case FROM_HAND -> {
                Part rising = rising(motif, style, strand, stage, scale);
                Vec3 target = stage.centre();
                yield (pen, progress) -> {
                    Vec3 hand = stage.hand(pen.partialTick());
                    if (hand == null || hand.distanceToSqr(target) < 1.5 * 1.5) {
                        rising.draw(pen, progress);
                        return;
                    }
                    segment(pen, motif, 0, 1, 0.0f);
                    pen.axial(style, hand, target, strand.width(), strand.halfBreadth(), strand.margin(), progress);
                };
            }
            case SKYFALL -> skyfall(motif, style, strand, stage, scale, random);
            case BURST -> burst(motif, style, strand, stage, scale, random);
            default -> rising(motif, style, strand, stage, scale);
        };
    }

    private static Part rising(FlourishMotif motif, int style, Strand strand, Stage stage, double scale) {
        Vec3 floor = stage.floor();
        Vec3 top = floor.add(0.0, Mth.clamp(2.5 + stage.extent() * 1.5, 2.5, 10.0) * scale, 0.0);
        return (pen, progress) -> {
            segment(pen, motif, 0, 1, 0.0f);
            pen.axial(style, floor, top, strand.width(), strand.halfBreadth(), strand.margin(), progress);
        };
    }

    private static Part skyfall(FlourishMotif motif, int style, Strand strand, Stage stage, double scale, Random random) {
        double spread = Mth.clamp(stage.extent() * 1.5, 0.8, 6.0) * scale;
        Vec3 floor = stage.floor();
        double slant = random.nextDouble() * Mth.TWO_PI;
        Vec3[] starts = new Vec3[FALLING];
        Vec3[] ends = new Vec3[FALLING];
        float[] delays = new float[FALLING];
        for (int i = 0; i < FALLING; i++) {
            ends[i] = floor.add((random.nextDouble() - 0.5) * spread, 0.05, (random.nextDouble() - 0.5) * spread);
            double lean = 1.5 + random.nextDouble();
            double drift = slant + (random.nextDouble() - 0.5) * 0.4;
            starts[i] = ends[i].add(Math.cos(drift) * lean, 7.0 + random.nextDouble() * 2.0, Math.sin(drift) * lean);
            delays[i] = i * 0.07f + random.nextFloat() * 0.05f;
        }
        return (pen, progress) -> {
            for (int i = 0; i < FALLING; i++) {
                segment(pen, motif, i, FALLING, delays[i]);
                pen.axial(style, starts[i], ends[i], strand.width(), strand.halfBreadth(), strand.margin(), progress);
            }
        };
    }

    private static Part burst(FlourishMotif motif, int style, Strand strand, Stage stage, double scale, Random random) {
        double reach = Mth.clamp(stage.extent() * 2.0, 1.2, 7.0) * scale;
        Vec3 centre = stage.centre();
        Vec3[] ends = new Vec3[BURSTING];
        for (int i = 0; i < BURSTING; i++) {
            double a = (i + random.nextDouble() * 0.6) * Mth.TWO_PI / BURSTING;
            Vec3 out = new Vec3(Math.cos(a), -0.6 + random.nextDouble() * 1.1, Math.sin(a)).normalize();
            ends[i] = centre.add(out.scale(reach * (0.6 + random.nextDouble() * 0.4)));
        }
        return (pen, progress) -> {
            for (int i = 0; i < BURSTING; i++) {
                segment(pen, motif, i, BURSTING, i * 0.07f);
                pen.axial(style, centre, ends[i], strand.width(), strand.halfBreadth(), strand.margin(), progress);
            }
        };
    }

    private static Part band(FlourishDesign.Layer layer, Stage stage) {
        double extent = stage.extent();
        double scale = layer.scale();
        double radius = Mth.clamp(extent * 1.2, 0.7, 5.0) * scale;
        double halfHeight = switch (layer.motif()) {
            case GLYPHS -> 0.22 * Math.sqrt(scale);
            case CHAINS -> 0.4 * Math.sqrt(scale);
            case BRIAR -> Mth.clamp(0.5 + extent * 0.3, 0.6, 1.8) * scale;
            default -> Mth.clamp(0.45 + extent * 0.35, 0.6, 2.0) * scale;
        };
        Vec3 centre = layer.placement() == FlourishMotif.Placement.FOOT
            ? stage.floor().add(0.0, halfHeight, 0.0)
            : stage.centre();
        int style = style(layer.motif());
        return (pen, progress) -> pen.band(style, centre, radius, halfHeight, progress);
    }

    record Mote(Vec3 at, Vec3 velocity, int colour) {
    }

    static List<Mote> motes(FlourishDesign.Motes motes, Stage stage, Random random) {
        double extent = stage.extent();
        Vec3 centre = stage.centre();
        Vec3 floor = stage.floor();
        List<Mote> thrown = new ArrayList<>();
        switch (motes) {
            case BURST -> {
                for (int i = 0; i < 32; i++) {
                    Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                        .normalize().scale(0.15);
                    thrown.add(new Mote(centre, out, i & 3));
                }
            }
            case RING -> {
                double radius = Mth.clamp(extent * 1.8, 1.0, 7.0) * 0.96;
                for (int i = 0; i < 24; i++) {
                    double a = i * Mth.TWO_PI / 24.0;
                    thrown.add(new Mote(floor.add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius),
                        new Vec3(0.0, 0.04, 0.0), i & 3));
                }
            }
            case RISE -> {
                double spread = Mth.clamp(extent * 0.6, 0.35, 2.0) * 0.5;
                for (int i = 0; i < 20; i++) {
                    thrown.add(new Mote(
                        floor.add((random.nextDouble() - 0.5) * spread, 0.1, (random.nextDouble() - 0.5) * spread),
                        new Vec3(0.0, 0.12 + random.nextDouble() * 0.08, 0.0), i & 3));
                }
            }
            case FALL -> {
                double radius = Mth.clamp(extent * 1.8, 1.2, 6.0);
                for (int i = 0; i < 16; i++) {
                    thrown.add(new Mote(centre.add((random.nextDouble() - 0.5) * radius * 1.2, radius * 0.5,
                        (random.nextDouble() - 0.5) * radius * 1.2), new Vec3(0.0, -0.02, 0.0), i & 3));
                }
            }
            case SPIRAL -> {
                double radius = Mth.clamp(extent * 2.0, 1.2, 8.0);
                for (int i = 0; i < 28; i++) {
                    double a = i * Mth.TWO_PI / 28.0;
                    double cos = Math.cos(a);
                    double sin = Math.sin(a);
                    thrown.add(new Mote(floor.add(cos * radius * 0.9, 0.1, sin * radius * 0.9),
                        new Vec3(-sin * 0.12 - cos * 0.06, 0.0, cos * 0.12 - sin * 0.06), i & 3));
                }
            }
            case NONE -> {
            }
        }
        return thrown;
    }

    private static void throwMotes(FlourishDesign.Motes motes, Stage stage, ClientLevel level, IntUnaryOperator colour) {
        for (Mote mote : motes(motes, stage, new Random())) {
            int argb = colour.applyAsInt(mote.colour());
            for (int i = 0; i < MOTE_LAYERS; i++) {
                FlourishLooks.mote(level, argb, mote.at(), mote.velocity().x, mote.velocity().y, mote.velocity().z);
            }
        }
    }
}
