package com.bluup.hexwright.client.malediction;

import at.petrak.hexcasting.common.particles.ConjureParticleOptions;
import com.bluup.hexwright.client.flourish.ClientFlourishes;
import com.bluup.hexwright.client.flourish.FlourishLook;
import com.bluup.hexwright.client.flourish.FlourishPen;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.bluup.hexwright.common.malediction.MaledictionSigil;
import com.bluup.hexwright.server.malediction.MaledictionDooms;
import com.bluup.hexwright.server.malediction.MaledictionProjectile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

public final class MaledictionSigils {

    private static final double HEAD_CLEARANCE_SQ = 1.6 * 1.6;
    private static final int HEAD_LIFETIME_TICKS = 400;
    private static final int MAX_CLOCKS = 32;
    private static final List<Clock> CLOCKS = new ArrayList<>();

    private MaledictionSigils() {
    }

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof MaledictionProjectile projectile) {
                ClientFlourishes.play(projectile.position(), (int) projectile.getUUID().getLeastSignificantBits(), new Head(projectile),
                    palette(() -> projectile.shownFinish().colour()));
            }
        });
    }

    public static void show(MaledictionSigil sigil) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity on = sigil.entityId() < 0 ? null : level.getEntity(sigil.entityId());
        if (sigil.kind() == MaledictionSigil.Kind.LIFTED) {
            if (on == null) {
                return;
            }
            CLOCKS.removeIf(clock -> {
                boolean lifted = clock.on == on;
                clock.lifted |= lifted;
                return lifted;
            });
            return;
        }
        FlourishLook look = switch (sigil.kind()) {
            case IMPACT -> new Impact(sigil.from(), sigil.size());
            case BURST -> new Burst(sigil.from(), sigil.size());
            case LANCE -> new Lance(sigil.from(), sigil.to());
            case LIGHTNING -> new Lightning(sigil.from(), sigil.to(), level.random.nextFloat());
            case BIND -> new Bind(on, sigil.from(), sigil.size());
            case DOOM -> clock(on, sigil.from(), sigil.size());
            case TOLL -> new Toll(on, sigil.from(), sigil.size());
            case LIFTED -> throw new IllegalStateException("handled above");
        };
        ClientFlourishes.play(sigil.from(), level.random.nextInt(), look, palette(sigil::colour));
    }

    private static ClientFlourishes.Palette palette(IntSupplier colour) {
        return (index, time) -> {
            int rgb = colour.getAsInt();
            float r = (rgb >> 16 & 0xFF) / 255.0F;
            float g = (rgb >> 8 & 0xFF) / 255.0F;
            float b = (rgb & 0xFF) / 255.0F;
            float toWhite = switch (index) {
                case 1 -> 0.35F;
                case 3 -> 0.6F;
                default -> 0.0F;
            };
            float scale = index == 2 ? 0.8F : 1.0F;
            return 0xFF000000
                | (int) (Mth.lerp(toWhite, r * scale, 1.0F) * 255.0F) << 16
                | (int) (Mth.lerp(toWhite, g * scale, 1.0F) * 255.0F) << 8
                | (int) (Mth.lerp(toWhite, b * scale, 1.0F) * 255.0F);
        };
    }

    private static Clock clock(@Nullable Entity on, Vec3 fallback, double size) {
        CLOCKS.removeIf(clock -> clock.lifted || clock.on == null || !clock.on.isAlive());
        while (CLOCKS.size() >= MAX_CLOCKS) {
            CLOCKS.remove(0);
        }
        Clock clock = new Clock(on, fallback, size);
        CLOCKS.add(clock);
        return clock;
    }

    private static double clockRadius(double size) {
        return size * 0.55;
    }

    private static Vec3 clockAt(@Nullable Entity on, Vec3 fallback, float partialTick, double radius) {
        return on(on, fallback, partialTick, 1.0).add(0.0, radius + 0.35, 0.0);
    }

    private static void sparks(ClientLevel level, IntUnaryOperator colour, Vec3 at, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Vec3 out = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian())
                .normalize().scale(speed);
            level.addParticle(new ConjureParticleOptions(colour.applyAsInt(i & 3)), at.x, at.y, at.z, out.x, out.y, out.z);
        }
    }

    private static Vec3 on(@Nullable Entity entity, Vec3 fallback, float partialTick, double up) {
        if (entity == null || entity.isRemoved()) {
            return fallback;
        }
        return entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * up, 0.0);
    }

    private record Head(MaledictionProjectile projectile) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return HEAD_LIFETIME_TICKS;
        }

        @Override
        public boolean finished() {
            return projectile.isRemoved();
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            Vec3 at = projectile.getPosition(pen.partialTick());
            if (at.distanceToSqr(pen.cameraPos()) < HEAD_CLEARANCE_SQ) {
                return;
            }
            MaledictionForm form = projectile.form();
            switch (form) {
                case VOLLEY -> pen.billboard(FlourishPen.STAR, at, 0.55, 0.14F);
                case SEEKER -> {
                    pen.billboard(FlourishPen.EYE, at, 0.7, 0.4F);
                    pen.billboard(FlourishPen.STAR, at, 0.4, 0.2F);
                }
                case ARC -> {
                    pen.billboard(FlourishPen.STAR, at, 0.7, 0.14F);
                    pen.segment(9.0F, 0.0F, 0.0F, 1.0F).billboard(FlourishPen.ORBIT, at, 0.5, 0.4F);
                }
                case THROW -> {
                    pen.billboard(FlourishPen.SHELL, at, 0.42, 0.4F);
                    pen.billboard(FlourishPen.STAR, at, 0.75, 0.14F);
                }
                case THREAD -> {
                    pen.billboard(FlourishPen.STAR, at, 0.7, 0.14F);
                    pen.segment(8.0F, 0.0F, 0.0F, 1.0F).billboard(FlourishPen.ORBIT, at, 0.42, 0.4F);
                    pen.segment(-6.0F, 0.5F, 0.0F, 1.0F).billboard(FlourishPen.ORBIT, at, 0.6, 0.4F);
                }
                default -> pen.billboard(FlourishPen.STAR, at, 0.85, 0.14F);
            }
        }
    }

    private record Impact(Vec3 at, double size) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 14;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            sparks(level, colour, at, 6, 0.08);
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.billboard(FlourishPen.STAR, at, size * 1.4, progress);
            pen.billboard(FlourishPen.RING, at, size * 1.8, Math.min(1.0F, progress * 1.2F));
        }
    }

    private record Burst(Vec3 at, double size) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 18;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.billboard(FlourishPen.STAR, at, size * 1.2, progress);
            pen.billboard(FlourishPen.RING, at, size * 1.3, Math.min(1.0F, progress * 1.3F));
            pen.disc(FlourishPen.RING, at, FlourishPen.UP, size * 1.6, progress);
        }
    }

    private record Lance(Vec3 from, Vec3 to) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 16;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.disc(FlourishPen.SEAL, from, to.subtract(from), 0.35, Math.min(1.0F, progress * 1.3F));
            pen.axial(FlourishPen.BEAM, from, to, 0.32, 0.6, 0.0, progress);
            pen.billboard(FlourishPen.STAR, to, 0.7, progress);
        }
    }

    private record Lightning(Vec3 from, Vec3 to, float seed) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 10;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            pen.segment(seed, 0.0F, 0.0F, 1.0F).axial(FlourishPen.ARC, from, to, 0.03, 0.8, 0.05, progress);
            pen.billboard(FlourishPen.STAR, to, 0.6, progress);
        }
    }

    private record Bind(@Nullable Entity on, Vec3 fallback, double size) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 24;
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            Vec3 at = MaledictionSigils.on(on, fallback, pen.partialTick(), 0.5);
            pen.band(FlourishPen.CHAINS, at, Math.max(size * 0.75, 0.45), 0.35, progress);
        }
    }

    private static final class Clock implements FlourishLook {
        private static final int FADE_TICKS = 6;

        final @Nullable Entity on;
        final Vec3 fallback;
        final double size;
        boolean lifted;

        Clock(@Nullable Entity on, Vec3 fallback, double size) {
            this.on = on;
            this.fallback = fallback;
            this.size = size;
        }

        @Override
        public int lifetimeTicks() {
            return MaledictionDooms.DOOM_TICKS + FADE_TICKS;
        }

        @Override
        public boolean finished() {
            return lifted || on != null && !on.isAlive();
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            float ticks = progress * lifetimeTicks();
            double radius = clockRadius(size);
            pen.segment(Mth.clamp((ticks - MaledictionDooms.DOOM_TICKS) / FADE_TICKS, 0.0F, 1.0F), 0.0F, 0.0F, 1.0F)
                .billboard(FlourishPen.CLOCK, clockAt(on, fallback, pen.partialTick(), radius), radius,
                    Math.min(ticks / MaledictionDooms.DOOM_TICKS, 1.0F));
        }
    }

    private record Toll(@Nullable Entity on, Vec3 fallback, double size) implements FlourishLook {
        @Override
        public int lifetimeTicks() {
            return 16;
        }

        @Override
        public void begin(ClientLevel level, IntUnaryOperator colour) {
            sparks(level, colour, fallback, 12, 0.12);
        }

        @Override
        public void draw(FlourishPen pen, float progress) {
            double radius = clockRadius(size);
            Vec3 clock = clockAt(on, fallback, pen.partialTick(), radius);
            pen.billboard(FlourishPen.RING, clock, radius * 2.2, progress);
            pen.billboard(FlourishPen.STAR, MaledictionSigils.on(on, fallback, pen.partialTick(), 0.5), size * 1.2, progress);
        }
    }
}
