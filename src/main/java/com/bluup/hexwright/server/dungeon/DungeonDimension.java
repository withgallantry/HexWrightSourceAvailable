package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class DungeonDimension {

    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, Hexwright.id("dungeons"));

    private DungeonDimension() {
    }

    public static @Nullable ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean isDungeonLevel(Level level) {
        return level.dimension().equals(KEY);
    }
}
