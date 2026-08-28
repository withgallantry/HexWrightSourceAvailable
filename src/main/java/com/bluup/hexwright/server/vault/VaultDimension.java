package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class VaultDimension {

    public static final ResourceKey<Level> KEY =
        ResourceKey.create(Registries.DIMENSION, Hexwright.id("vaults"));

    public static final int HEIGHT = 96;

    private VaultDimension() {
    }

    public static @Nullable ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean isVaultLevel(Level level) {
        return level.dimension().equals(KEY);
    }
}
