package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.region.Region;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class MaledictionFx {

    private static final int FLARE_MOTES = 48;
    private static final int FLARE_TRIES = 8;

    private static int nextWarning;

    private MaledictionFx() {
    }

    public static DustParticleOptions dust(MaledictionFinish finish, float size) {
        int colour = finish.colour();
        return new DustParticleOptions(new Vector3f(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
            (colour & 0xFF) / 255.0F), size);
    }

    public static void burst(ServerLevel level, Vec3 at, MaledictionFinish finish, int count, double spread) {
        level.sendParticles(dust(finish, 1.2F), at.x, at.y, at.z, count, spread, spread, spread, 0.02);
    }

    public static void line(ServerLevel level, Vec3 from, Vec3 to, MaledictionFinish finish) {
        DustParticleOptions dust = dust(finish, 0.9F);
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 3.0));
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.lerp(to, i / (double) steps);
            level.sendParticles(dust, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    public static void fill(ServerLevel level, Region region, MaledictionFinish finish) {
        DustParticleOptions dust = dust(finish, 1.6F);
        AABB bounds = region.bounds();
        RandomSource random = level.getRandom();
        for (int i = 0; i < FLARE_MOTES; i++) {
            for (int attempt = 0; attempt < FLARE_TRIES; attempt++) {
                double x = bounds.minX + random.nextDouble() * bounds.getXsize();
                double y = bounds.minY + random.nextDouble() * bounds.getYsize();
                double z = bounds.minZ + random.nextDouble() * bounds.getZsize();
                if (region.contains(x, y, z)) {
                    level.sendParticles(dust, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
                    break;
                }
            }
        }
    }

    public static int warn(ServerLevel level, Region region, MaledictionFinish finish, int landTicks, int lifetimeTicks) {
        int id = nextWarning++;
        HexwrightNetworking.sendMaledictionWarning(level, region, id, finish.colour(), landTicks, lifetimeTicks);
        return id;
    }

    public static void landed(ServerLevel level, Region region, int warning, boolean landed, int nextTicks) {
        HexwrightNetworking.sendMaledictionLanding(level, region, warning, landed, nextTicks);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
