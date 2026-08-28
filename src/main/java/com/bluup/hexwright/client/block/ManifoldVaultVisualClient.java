package com.bluup.hexwright.client.block;

import com.lowdragmc.photon.client.fx.BlockEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ManifoldVaultVisualClient {

    private static final TrackedBlockEffect MANIFOLD = new TrackedBlockEffect(
        new ResourceLocation("hexwright", "manifold"), null, null);

    private ManifoldVaultVisualClient() {
    }

    public static void setActive(Level level, BlockPos pos, boolean active) {
        MANIFOLD.setActive(level, pos, active);
    }

    public static @Nullable BlockEffect startEffect(Level level, BlockPos pos) {
        return MANIFOLD.start(level, pos);
    }

    public static void onParticlesCleared() {
        MANIFOLD.forgetAll();
    }
}
