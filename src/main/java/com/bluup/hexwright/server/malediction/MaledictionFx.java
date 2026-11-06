package com.bluup.hexwright.server.malediction;

import at.petrak.hexcasting.common.particles.ConjureParticleOptions;
import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionSigil;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.region.Region;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class MaledictionFx {

    private static final int FLARE_MOTES = 64;
    private static final int FLARE_TRIES = 8;

    private static int nextWarning;

    private MaledictionFx() {
    }

    public static ConjureParticleOptions spark(MaledictionFinish finish) {
        return new ConjureParticleOptions(finish.colour());
    }

    public static void burst(ServerLevel level, Vec3 at, MaledictionFinish finish, int count, double spread) {
        level.sendParticles(spark(finish), at.x, at.y, at.z, count, spread, spread, spread, 0.04);
    }

    public static void sigil(ServerLevel level, MaledictionSigil.Kind kind, MaledictionFinish finish, Vec3 at, float size) {
        sigil(level, kind, finish, at, at, size, null);
    }

    public static void sigil(ServerLevel level, MaledictionSigil.Kind kind, MaledictionFinish finish, Vec3 from, Vec3 to,
                             float size, @Nullable Entity on) {
        HexwrightNetworking.sendMaledictionSigil(level,
            new MaledictionSigil(kind, finish.colour(), from, to, size, on == null ? -1 : on.getId()));
    }

    public static void fill(ServerLevel level, Region region, MaledictionFinish finish) {
        ConjureParticleOptions spark = spark(finish);
        AABB bounds = region.bounds();
        RandomSource random = level.getRandom();
        for (int i = 0; i < FLARE_MOTES; i++) {
            for (int attempt = 0; attempt < FLARE_TRIES; attempt++) {
                double x = bounds.minX + random.nextDouble() * bounds.getXsize();
                double y = bounds.minY + random.nextDouble() * bounds.getYsize();
                double z = bounds.minZ + random.nextDouble() * bounds.getZsize();
                if (region.contains(x, y, z)) {
                    level.sendParticles(spark, x, y, z, 1, 0.05, 0.05, 0.05, 0.03);
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
