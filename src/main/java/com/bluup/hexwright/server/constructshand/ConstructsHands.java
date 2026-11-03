package com.bluup.hexwright.server.constructshand;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConstructsHands {

    private record Location(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<Location, List<HexAngle>> KEYS = new ConcurrentHashMap<>();

    private ConstructsHands() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> KEYS.clear());
    }

    static void track(Level level, BlockPos pos, @Nullable HexPattern key) {
        Location at = new Location(level.dimension(), pos.immutable());
        if (key == null) {
            KEYS.remove(at);
        } else {
            KEYS.put(at, List.copyOf(key.getAngles()));
        }
    }

    static void untrack(Level level, BlockPos pos) {
        KEYS.remove(new Location(level.dimension(), pos.immutable()));
    }

    static @Nullable BlockPos nearestKeyed(CastingEnvironment env, HexPattern pattern) {
        if (KEYS.isEmpty()) {
            return null;
        }
        ResourceKey<Level> dimension = env.getWorld().dimension();
        List<HexAngle> angles = pattern.getAngles();
        Vec3 from = null;
        BlockPos nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (Map.Entry<Location, List<HexAngle>> entry : KEYS.entrySet()) {
            Location at = entry.getKey();
            if (!at.dimension().equals(dimension) || !entry.getValue().equals(angles)) {
                continue;
            }
            Vec3 centre = Vec3.atCenterOf(at.pos());
            if (!env.isVecInAmbit(centre)) {
                continue;
            }
            if (from == null) {
                from = env.mishapSprayPos();
            }
            double distanceSqr = centre.distanceToSqr(from);
            if (distanceSqr < nearestSqr
                || distanceSqr == nearestSqr && nearest != null && at.pos().compareTo(nearest) < 0) {
                nearest = at.pos();
                nearestSqr = distanceSqr;
            }
        }
        return nearest;
    }
}
