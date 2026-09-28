package com.bluup.hexwright.client.cloak;

import com.bluup.hexwright.server.cloak.CloakField;
import it.unimi.dsi.fastutil.HashCommon;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CloakFieldRipples {

    public static final int MAX_PER_FIELD = 4;
    private static final long LIFETIME_NANOS = 1_400_000_000L;

    private static final double SWELL_PERIOD_SECONDS = 11.0;
    private static final double SWELL_BLOCKS_PER_SECOND = 10.0;
    private static final double SWELL_MIN_SECONDS = 3.5;
    private static final double SWELL_MAX_SECONDS = 8.0;

    public record Ripple(Vec3 point, long startNanos) {
    }

    public record Swell(Vec3 point, float age) {
    }

    private static final Map<UUID, ArrayDeque<Ripple>> RIPPLES = new HashMap<>();

    private CloakFieldRipples() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                RIPPLES.clear();
                return;
            }
            long now = System.nanoTime();
            RIPPLES.values().forEach(ripples -> ripples.removeIf(r -> now - r.startNanos() >= LIFETIME_NANOS));
            RIPPLES.values().removeIf(ArrayDeque::isEmpty);
        });
    }

    public static void add(UUID fieldId, Vec3 point) {
        ArrayDeque<Ripple> ripples = RIPPLES.computeIfAbsent(fieldId, id -> new ArrayDeque<>());
        if (ripples.size() >= MAX_PER_FIELD) {
            ripples.pollFirst();
        }
        ripples.addLast(new Ripple(point, System.nanoTime()));
    }

    public static float age(Ripple ripple, long now) {
        return (float) (now - ripple.startNanos()) / LIFETIME_NANOS;
    }

    public static Iterable<Ripple> of(CloakField field) {
        ArrayDeque<Ripple> ripples = RIPPLES.get(field.id());
        return ripples == null ? List.of() : ripples;
    }

    public static @Nullable Swell swell(CloakField field, long now) {
        double reach = 2.0 * Math.max(field.radius(), field.height());
        double life = Mth.clamp(reach / SWELL_BLOCKS_PER_SECOND, SWELL_MIN_SECONDS, SWELL_MAX_SECONDS);
        double seconds = now / 1.0e9;
        long window = (long) Math.floor(seconds / SWELL_PERIOD_SECONDS);
        long key = field.id().getMostSignificantBits() + window * 0x9E3779B97F4A7C15L;
        double start = window * SWELL_PERIOD_SECONDS + random(key, 1) * (SWELL_PERIOD_SECONDS - life);
        float age = (float) ((seconds - start) / life);
        if (age < 0.0f || age >= 1.0f) {
            return null;
        }

        float azimuth = Mth.TWO_PI * random(key, 2);
        float elevation = Mth.HALF_PI * 0.6f * random(key, 3);
        float across = Mth.cos(elevation) * field.radius();
        Vec3 c = field.center();
        Vec3 point = new Vec3(
            c.x + Mth.cos(azimuth) * across,
            c.y + Mth.sin(elevation) * field.height(),
            c.z + Mth.sin(azimuth) * across);
        return new Swell(point, age);
    }

    private static float random(long key, int salt) {
        return (HashCommon.mix(key + salt * 0xC2B2AE3D27D4EB4FL) >>> 40) / (float) (1L << 24);
    }
}
