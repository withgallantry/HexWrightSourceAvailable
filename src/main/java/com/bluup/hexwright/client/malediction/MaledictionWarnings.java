package com.bluup.hexwright.client.malediction;

import at.petrak.hexcasting.common.particles.ConjureParticleOptions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.client.region.RegionGrid;
import com.bluup.hexwright.client.region.RegionVeil;
import com.bluup.hexwright.server.region.Region;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MaledictionWarnings {

    private static final long FADE_OUT_MS = 400L;
    private static final long FLASH_MS = 350L;
    private static final long SETTLE_MS = 600L;
    private static final int MAX_WARNINGS = 16;

    private static final int POINTS_PER_SPARK = 150;
    private static final int MAX_SPARKS_PER_TICK = 6;
    private static final int WAITING_SPARK_ODDS = 4;
    private static final double SPARK_RISE = 0.03;
    private static final int POINTS_PER_BURST_SPARK = 25;
    private static final int MIN_BURST_SPARKS = 16;
    private static final int MAX_BURST_SPARKS = 80;
    private static final double BURST_SPEED = 0.12;

    private static final List<Warning> LIVE = new ArrayList<>();

    private MaledictionWarnings() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            if (!LIVE.isEmpty()) {
                RegionVeil.captureOpaqueDepth();
            }
        });
        WorldRenderEvents.LAST.register(MaledictionWarnings::render);
        ClientTickEvents.END_CLIENT_TICK.register(MaledictionWarnings::tick);
    }

    public static void warn(int id, @Nullable CompoundTag regionTag, int colour, int landTicks, int lifetimeTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || regionTag == null) {
            return;
        }
        Region region;
        try {
            region = Region.load(regionTag);
        } catch (IllegalArgumentException malformed) {
            Hexwright.LOGGER.warn("Ignoring a malformed Malediction warning", malformed);
            return;
        }
        if (region.isEmpty()) {
            return;
        }
        CompletableFuture<RegionVeil.Shape> shape = CompletableFuture.supplyAsync(() -> trace(region), Util.backgroundExecutor())
            .exceptionally(failure -> {
                Hexwright.LOGGER.error("A Malediction warning could not trace its Region", failure);
                return null;
            });
        while (LIVE.size() >= MAX_WARNINGS) {
            LIVE.remove(0).close();
        }
        long now = Util.getMillis();
        LIVE.add(new Warning(id, level, colour, shape, now, now + landTicks * 50L, now + lifetimeTicks * 50L));
    }

    public static void landed(int id, boolean landed, int nextTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        long now = Util.getMillis();
        for (Warning warning : LIVE) {
            if (warning.id == id && warning.level == level) {
                warning.landed(now, landed, nextTicks);
            }
        }
    }

    private static RegionVeil.Shape trace(Region region) {
        long started = System.nanoTime();
        RegionVeil.Shape shape = RegionVeil.Shape.trace(region);
        RegionGrid grid = shape.grid();
        HexwrightDebug.log(HexwrightDebug.RENDER, "[malediction] warning sampled {}x{}x{} at {} in {} ms",
            grid.nx(), grid.ny(), grid.nz(), grid.spacing(), (System.nanoTime() - started) / 1_000_000L);
        return shape;
    }

    private static void tick(Minecraft mc) {
        if (LIVE.isEmpty() || mc.level == null || mc.isPaused()) {
            return;
        }
        long now = Util.getMillis();
        for (Warning warning : LIVE) {
            if (warning.level == mc.level) {
                warning.spark(mc.level, now);
            }
        }
    }

    private static void render(WorldRenderContext context) {
        if (LIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        LIVE.removeIf(warning -> {
            boolean over = now >= warning.endsAt;
            if (over) {
                warning.close();
            }
            return over;
        });
        if (mc.level == null || LIVE.isEmpty()) {
            return;
        }

        Frustum frustum = context.frustum();
        List<Warning> visible = new ArrayList<>(LIVE.size());
        for (Warning warning : LIVE) {
            RegionVeil.Shape shape = warning.shape.getNow(null);
            if (warning.level == mc.level && shape != null
                && (frustum == null || frustum.isVisible(shape.grid().box()))) {
                visible.add(warning);
            }
        }
        if (visible.isEmpty() || !RegionVeil.begin(context)) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        try {
            for (Warning warning : visible) {
                RegionVeil.draw(warning.shape.getNow(null), warning.colour, (warning.id * 0.618034F) % 1.0F * 100.0F,
                    warning.moment(now), camera);
            }
        } finally {
            RegionVeil.end();
        }
    }

    private static final class Warning {

        final int id;
        final ClientLevel level;
        final int colour;
        final CompletableFuture<RegionVeil.Shape> shape;
        final long shownAt;
        final RandomSource random = RandomSource.create();
        long chargedFrom;
        long landsAt;
        long endsAt;
        boolean flashed;
        long flashedAt;
        boolean burstDue;

        Warning(int id, ClientLevel level, int colour, CompletableFuture<RegionVeil.Shape> shape, long shownAt,
                long landsAt, long endsAt) {
            this.id = id;
            this.level = level;
            this.colour = colour;
            this.shape = shape;
            this.shownAt = shownAt;
            this.chargedFrom = shownAt;
            this.landsAt = landsAt;
            this.endsAt = endsAt;
        }

        void landed(long now, boolean landed, int nextTicks) {
            if (!landed) {
                endsAt = Math.min(endsAt, now + FADE_OUT_MS);
                return;
            }
            flashed = true;
            flashedAt = now;
            burstDue = true;
            if (nextTicks > 0) {
                chargedFrom = now;
                landsAt = now + nextTicks * 50L;
            } else {
                landsAt = now;
                endsAt = Math.min(endsAt, now + FLASH_MS + FADE_OUT_MS);
            }
        }

        void close() {
            RegionVeil.Shape traced = shape.getNow(null);
            if (traced != null) {
                traced.close();
            }
        }

        private float charge(long now) {
            return now < landsAt ? (now - chargedFrom) / (float) (landsAt - chargedFrom) : 1.0F;
        }

        RegionVeil.Moment moment(long now) {
            float flash = flashed ? Math.max(0.0F, 1.0F - (now - flashedAt) / (float) FLASH_MS) : 0.0F;
            float gather = now < landsAt ? 1.0F : Math.max(0.0F, 1.0F - (now - landsAt) / (float) SETTLE_MS);
            float activation = Math.min((now - shownAt) / (float) RegionVeil.look().activationMs(), 1.0F);
            float fade = Mth.clamp((endsAt - now) / (float) FADE_OUT_MS, 0.0F, 1.0F);
            return new RegionVeil.Moment(activation, charge(now), gather, flash * flash, fade);
        }

        void spark(ClientLevel clientLevel, long now) {
            RegionVeil.Shape traced = shape.getNow(null);
            if (traced == null || traced.grid().surfaceCount() == 0 || now >= endsAt) {
                return;
            }
            RegionGrid grid = traced.grid();
            int points = grid.surfaceCount();
            ConjureParticleOptions spark = new ConjureParticleOptions(colour);
            if (burstDue) {
                burstDue = false;
                int count = Mth.clamp(points / POINTS_PER_BURST_SPARK, MIN_BURST_SPARKS, MAX_BURST_SPARKS);
                for (int i = 0; i < count; i++) {
                    emit(clientLevel, grid, spark, random.nextInt(points), BURST_SPEED * (0.5 + random.nextDouble()), 0.0);
                }
            }
            boolean charging = now < landsAt;
            int count = charging ? Mth.clamp(points / POINTS_PER_SPARK, 1, MAX_SPARKS_PER_TICK)
                : random.nextInt(WAITING_SPARK_ODDS) == 0 ? 1 : 0;
            float frontY = traced.bottom() + charge(now) * (traced.top() - traced.bottom());
            for (int i = 0; i < count; i++) {
                int point = random.nextInt(points);
                if (grid.surface()[point * 3 + 1] <= frontY) {
                    emit(clientLevel, grid, spark, point, 0.0, SPARK_RISE * (0.5 + random.nextDouble()));
                }
            }
        }

        private void emit(ClientLevel clientLevel, RegionGrid grid, ConjureParticleOptions spark, int point,
                          double outward, double rise) {
            float[] surface = grid.surface();
            float[] normals = grid.normals();
            int at = point * 3;
            clientLevel.addParticle(spark,
                grid.originX() + surface[at], grid.originY() + surface[at + 1], grid.originZ() + surface[at + 2],
                normals[at] * outward, normals[at + 1] * outward + rise, normals[at + 2] * outward);
        }
    }
}
