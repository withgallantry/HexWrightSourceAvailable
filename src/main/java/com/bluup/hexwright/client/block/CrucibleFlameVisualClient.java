package com.bluup.hexwright.client.block;

import com.lowdragmc.photon.client.fx.BlockEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public final class CrucibleFlameVisualClient {

    private static final TrackedBlockEffect FLAME = new TrackedBlockEffect(
        new ResourceLocation("hexwright", "crucible_flame"), new Vector3f(0.0f, 0.1f, 0.0f), null);

    private CrucibleFlameVisualClient() {
    }

    public static void setActive(Level level, BlockPos pos, boolean active) {
        FLAME.setActive(level, pos, active);
    }

    public static @Nullable BlockEffect startEffect(Level level, BlockPos pos) {
        return FLAME.start(level, pos);
    }

    public static void onParticlesCleared() {
        FLAME.forgetAll();
    }
}
