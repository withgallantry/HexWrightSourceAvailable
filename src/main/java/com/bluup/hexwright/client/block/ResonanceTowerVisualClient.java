package com.bluup.hexwright.client.block;

import com.lowdragmc.photon.client.fx.BlockEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public final class ResonanceTowerVisualClient {

    private static final TrackedBlockEffect TOWER = new TrackedBlockEffect(
        new ResourceLocation("hexwright", "resonance_tower"),
        new Vector3f(0.0f, 0.8f, 0.0f), new Vector3f(0.6f, 0.6f, 0.6f));

    private ResonanceTowerVisualClient() {
    }

    public static void setActive(Level level, BlockPos pos, boolean active) {
        TOWER.setActive(level, pos, active);
    }

    public static @Nullable BlockEffect startEffect(Level level, BlockPos pos) {
        return TOWER.start(level, pos);
    }

    public static void onParticlesCleared() {
        TOWER.forgetAll();
    }
}
