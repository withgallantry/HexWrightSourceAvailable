package com.bluup.hexwright.client.inversion;

import com.bluup.hexwright.server.inversion.Inversion;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class ClientInversions {

    private static final double MOTES_WITHIN = 72.0;
    private static final int MOTES_PER_TICK = 6;

    private static final RandomSource RANDOM = RandomSource.create();

    private static List<Inversion> inversions = List.of();
    private static ClientLevel boundLevel;

    private ClientInversions() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                inversions = List.of();
            }
            if (client.level != null && client.player != null && !client.isPaused()) {
                inversions.forEach(inversion -> motes(client.level, inversion, client.player.position()));
            }
        });
    }

    public static void set(List<Inversion> received) {
        boundLevel = Minecraft.getInstance().level;
        inversions = List.copyOf(received);
    }

    public static List<Inversion> in(ClientLevel level) {
        return level == boundLevel ? inversions : List.of();
    }

    private static void motes(ClientLevel level, Inversion inversion, Vec3 player) {
        if (inversion.phase() == Inversion.Phase.HELD) {
            return;
        }
        double front = inversion.front(level.getGameTime());
        double toPlayer = Math.hypot(player.x - inversion.x(), player.z - inversion.z());
        if (Math.abs(toPlayer - front) > MOTES_WITHIN) {
            return;
        }
        double facing = Math.atan2(player.z - inversion.z(), player.x - inversion.x());
        double spread = MOTES_WITHIN / Math.max(front, MOTES_WITHIN);
        for (int i = 0; i < MOTES_PER_TICK; i++) {
            double angle = facing + (RANDOM.nextDouble() * 2.0 - 1.0) * spread;
            double x = inversion.x() + Math.cos(angle) * front;
            double z = inversion.z() + Math.sin(angle) * front;
            if (!inversion.covers(x, z)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));
            level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y + RANDOM.nextDouble() * 2.0, z,
                0.0, 0.05 + RANDOM.nextDouble() * 0.1, 0.0);
        }
    }
}
