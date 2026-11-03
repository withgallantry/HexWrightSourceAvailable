package com.bluup.hexwright.server.region;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RegionSpills {

    @Nullable
    private static RegionSpills catching;

    private final ServerLevel level;
    private final Thread owner;
    private final List<Entity> caught = new ArrayList<>();

    private RegionSpills(ServerLevel level) {
        this.level = level;
        this.owner = Thread.currentThread();
    }

    public static List<Entity> catchWhile(ServerLevel level, Runnable removal) {
        RegionSpills outer = catching;
        RegionSpills spills = new RegionSpills(level);
        catching = spills;
        try {
            removal.run();
        } finally {
            catching = outer;
        }
        return spills.caught;
    }

    public static boolean isCatching(Level level) {
        RegionSpills spills = catching;
        return spills != null && spills.level == level && spills.owner == Thread.currentThread();
    }

    public static boolean hold(Level level, Entity entity) {
        if (!isCatching(level)) {
            return false;
        }
        catching.caught.add(entity);
        return true;
    }
}
