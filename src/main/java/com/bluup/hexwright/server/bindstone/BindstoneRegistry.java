package com.bluup.hexwright.server.bindstone;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BindstoneRegistry {

    private record Location(ResourceKey<Level> dimension, BlockPos origin) {
    }

    private record Ward(Vec3 centre, boolean sealsBlocks) {
    }

    private static final Map<Location, Ward> WARDS = new ConcurrentHashMap<>();

    private static final AtomicInteger SEALING = new AtomicInteger();

    private BindstoneRegistry() {
    }

    public static void registerPillar(Level level, BlockPos origin) {
        put(level, origin, new Ward(BindstonePillar.wardCentre(origin), true));
    }

    public static void registerCube(Level level, BlockPos centre) {
        put(level, centre, new Ward(Vec3.atCenterOf(centre), false));
    }

    private static void put(Level level, BlockPos origin, Ward ward) {
        Ward previous = WARDS.put(new Location(level.dimension(), origin.immutable()), ward);
        int delta = (ward.sealsBlocks() ? 1 : 0) - (previous != null && previous.sealsBlocks() ? 1 : 0);
        if (delta != 0) {
            SEALING.addAndGet(delta);
        }
    }

    public static void unregister(Level level, BlockPos origin) {
        Ward previous = WARDS.remove(new Location(level.dimension(), origin.immutable()));
        if (previous != null && previous.sealsBlocks()) {
            SEALING.decrementAndGet();
        }
    }

    public static boolean isEmpty() {
        return WARDS.isEmpty();
    }

    public static boolean sealsAnything() {
        return SEALING.get() > 0;
    }

    public static boolean isWarded(Level level, Vec3 point) {
        return any(level, point, false);
    }

    public static boolean isWarded(Level level, BlockPos pos) {
        return isWarded(level, Vec3.atCenterOf(pos));
    }

    public static boolean sealsEdits(Level level, BlockPos pos) {
        return SEALING.get() > 0 && any(level, Vec3.atCenterOf(pos), true);
    }

    private static boolean any(Level level, Vec3 point, boolean sealingOnly) {
        if (WARDS.isEmpty()) {
            return false;
        }
        ResourceKey<Level> dimension = level.dimension();
        double radiusSqr = BindstonePillar.INFLUENCE_RADIUS * BindstonePillar.INFLUENCE_RADIUS;
        for (Map.Entry<Location, Ward> entry : WARDS.entrySet()) {
            Ward ward = entry.getValue();
            if (sealingOnly && !ward.sealsBlocks()) {
                continue;
            }
            if (entry.getKey().dimension().equals(dimension) && ward.centre().distanceToSqr(point) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        WARDS.clear();
        SEALING.set(0);
    }
}
