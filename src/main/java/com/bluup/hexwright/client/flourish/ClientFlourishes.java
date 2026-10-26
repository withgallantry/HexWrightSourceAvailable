package com.bluup.hexwright.client.flourish;

import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.client.ClientTickCounter;
import at.petrak.hexcasting.common.particles.ConjureParticleOptions;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.server.flourish.FlourishCast;
import com.bluup.hexwright.server.flourish.FlourishSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;

public final class ClientFlourishes {

    private static final double NANOS_PER_TICK = 50_000_000.0;

    static final double MAX_DRAW_DISTANCE_SQ = 64.0 * 64.0;

    private static final int MAX_LIVE = 96;

    private static final float SOUND_VOLUME = 1.0f;

    private static final Vec3[] PROBES = {
        new Vec3(3.0, 0.0, 0.0),
        new Vec3(0.0, 3.0, 0.0),
        new Vec3(0.0, 0.0, 3.0),
        new Vec3(-1.73, -1.73, -1.73),
    };

    private static final int[] FALLBACK = {0xFF9E61FF, 0xFFD99EFF, 0xFF7A4DEB, 0xFFF2CCFF};

    static final class Live {
        final FlourishCast cast;
        final FlourishLook look;
        final long startNanos;
        @Nullable
        final ColorProvider colours;

        private Live(FlourishCast cast, FlourishLook look, long startNanos, @Nullable ColorProvider colours) {
            this.cast = cast;
            this.look = look;
            this.startNanos = startNanos;
            this.colours = colours;
        }

        float ticks(long now) {
            return (float) ((now - startNanos) / NANOS_PER_TICK);
        }

        float seed() {
            return (cast.seed() >>> 16 & 0xFFFF) / 65535.0f * 64.0f;
        }

        int colour(int index, float time) {
            return ClientFlourishes.colour(colours, index, time);
        }
    }

    private static final List<Live> LIVE = new ArrayList<>();

    private static ClientLevel boundLevel;

    private ClientFlourishes() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                LIVE.clear();
            }
        });
    }

    public static void add(FlourishCast cast) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level != boundLevel) {
            boundLevel = level;
            LIVE.clear();
        }
        level.playLocalSound(cast.pos().x, cast.pos().y, cast.pos().z, FlourishSounds.get(cast.flourish()),
            SoundSource.PLAYERS, SOUND_VOLUME, 0.94f + level.random.nextFloat() * 0.12f, false);
        ColorProvider colours = resolve(cast.pigment());
        float time = ClientTickCounter.getTotal();
        IntUnaryOperator colour = index -> colour(colours, index, time);
        if (FlourishShaders.shader() == null) {
            spray(level, cast, colour);
            return;
        }
        FlourishLook look = FlourishLooks.create(cast, level);
        look.begin(level, colour);
        if (LIVE.size() >= MAX_LIVE) {
            LIVE.remove(0);
        }
        LIVE.add(new Live(cast, look, System.nanoTime(), colours));
    }

    static List<Live> live() {
        if (!LIVE.isEmpty()) {
            long now = System.nanoTime();
            LIVE.removeIf(live -> live.ticks(now) >= live.look.lifetimeTicks());
        }
        return LIVE;
    }

    private static @Nullable ColorProvider resolve(@Nullable CompoundTag pigment) {
        if (pigment == null) {
            return null;
        }
        try {
            return IXplatAbstractions.INSTANCE.getColorProvider(FrozenPigment.fromNBT(pigment));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int colour(@Nullable ColorProvider colours, int index, float time) {
        if (colours != null) {
            try {
                return colours.getColor(time, PROBES[index]);
            } catch (RuntimeException ignored) {
            }
        }
        return FALLBACK[index];
    }

    private static void spray(ClientLevel level, FlourishCast cast, IntUnaryOperator colour) {
        Random random = new Random();
        double speed = cast.extent() / 20.0;
        for (int i = 0; i < 16; i++) {
            Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            level.addParticle(new ConjureParticleOptions(colour.applyAsInt(i & 3)),
                cast.pos().x, cast.pos().y, cast.pos().z, out.x * speed, out.y * speed, out.z * speed);
        }
    }
}
