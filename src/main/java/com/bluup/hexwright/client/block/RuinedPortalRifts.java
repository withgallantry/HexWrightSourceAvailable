package com.bluup.hexwright.client.block;

import com.bluup.hexwright.client.portal.VoidTearRenderer;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RuinedPortalRifts {


    private static final double CENTER_Y = 1.0;

    private static final double HALF_LENGTH = 0.75;

    private static final double HALF_APERTURE = 0.52;

    private static final double MAX_DRAW_DISTANCE_SQ = 96.0 * 96.0;


    private static final int SLOT_TICKS = 130;

    private static final int RISE_TICKS = 4;
    private static final int HOLD_TICKS = 5;
    private static final int FALL_TICKS = 18;
    private static final int FLARE_TICKS = RISE_TICKS + HOLD_TICKS + FALL_TICKS;

    private static final float QUIET_CHANCE = 0.22f;

    private static final float IDLE_PROGRESS = 0.30f;

    private static final float MIN_PEAK = 0.46f;
    private static final float MAX_PEAK = 0.95f;

    private static final float INTENSITY_BIAS = 1.7f;

    private static final float MIN_VOLUME = 0.50f;
    private static final float MAX_VOLUME = 1.00f;
    private static final float MIN_PITCH = 0.72f;
    private static final float MAX_PITCH = 1.45f;
    private static final float PITCH_JITTER = 0.12f;


    private static final Map<BlockPos, Long> LOADED = new HashMap<>();

    private static final long STALE_TICKS = 5;

    private static ClientLevel boundLevel;

    private RuinedPortalRifts() {
    }

    public static void register() {
        VoidTearRenderer.addSource(RuinedPortalRifts::collect);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                LOADED.clear();
            }
        });
    }

    public static void clientTick(Level level, BlockPos pos) {
        long now = level.getGameTime();
        LOADED.put(pos.immutable(), now);

        Flare flare = flareAt(seedOf(pos), now);
        if (flare == null || flare.start() != now) {
            return;
        }
        float pitch = Mth.clamp(
            Mth.lerp(flare.intensity(), MAX_PITCH, MIN_PITCH)
                + (flare.pitchJitter() - 0.5f) * PITCH_JITTER,
            0.5f, 2.0f);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + CENTER_Y, pos.getZ() + 0.5,
            HexwrightSoundEvents.ruinedPortalFlare(), SoundSource.BLOCKS,
            Mth.lerp(flare.intensity(), MIN_VOLUME, MAX_VOLUME), pitch, false);
    }

    public static void removed(BlockPos pos) {
        LOADED.remove(pos);
    }

    private static void collect(Vec3 cameraPos, List<VoidTearRenderer.Rift> into) {
        if (LOADED.isEmpty()) {
            return;
        }
        ClientLevel level = boundLevel;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        LOADED.entrySet().removeIf(entry -> now - entry.getValue() > STALE_TICKS);

        double time = now + Minecraft.getInstance().getFrameTime();
        for (BlockPos pos : LOADED.keySet()) {
            Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + CENTER_Y, pos.getZ() + 0.5);
            if (center.distanceToSqr(cameraPos) > MAX_DRAW_DISTANCE_SQ) {
                continue;
            }
            long seed = seedOf(pos);
            into.add(new VoidTearRenderer.Rift(
                center,
                new Vec3(0.0, HALF_LENGTH, 0.0),
                new Vec3(HALF_APERTURE, 0.0, 0.0),
                shaderSeed(seed),
                progressAt(seed, time)));
        }
    }


    private record Flare(long start, float intensity, float pitchJitter) {
    }

    private static Flare flareAt(long seed, long tick) {
        long slot = Math.floorDiv(tick, SLOT_TICKS);
        long hash = mix(seed, slot);
        if (unit(hash, 0) < QUIET_CHANCE) {
            return null;
        }
        long start = slot * SLOT_TICKS + (long) (unit(hash, 1) * (SLOT_TICKS - FLARE_TICKS));
        float intensity = (float) Math.pow(unit(hash, 2), INTENSITY_BIAS);
        return new Flare(start, intensity, unit(hash, 3));
    }

    private static float progressAt(long seed, double time) {
        Flare flare = flareAt(seed, (long) Math.floor(time));
        if (flare == null) {
            return IDLE_PROGRESS;
        }
        float envelope = envelope(time - flare.start());
        if (envelope <= 0.0f) {
            return IDLE_PROGRESS;
        }
        float peak = Mth.lerp(flare.intensity(), MIN_PEAK, MAX_PEAK);
        return IDLE_PROGRESS + (peak - IDLE_PROGRESS) * envelope;
    }

    private static float envelope(double elapsed) {
        if (elapsed <= 0.0 || elapsed >= FLARE_TICKS) {
            return 0.0f;
        }
        if (elapsed < RISE_TICKS) {
            float t = (float) (elapsed / RISE_TICKS);
            return t * t * (3.0f - 2.0f * t);
        }
        if (elapsed < RISE_TICKS + HOLD_TICKS) {
            return 1.0f;
        }
        float t = (float) ((elapsed - RISE_TICKS - HOLD_TICKS) / FALL_TICKS);
        return (1.0f - t) * (1.0f - t);
    }

    private static long seedOf(BlockPos pos) {
        return Mth.getSeed(pos.getX(), pos.getY(), pos.getZ());
    }

    private static float shaderSeed(long seed) {
        return (float) ((seed >>> 16 & 0xFFFFL) / 65535.0 * 64.0);
    }

    private static long mix(long seed, long slot) {
        long z = seed * 0x9E3779B97F4A7C15L + slot * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long hash, int channel) {
        return ((hash >>> (channel * 16)) & 0xFFFFL) / 65535.0f;
    }
}
