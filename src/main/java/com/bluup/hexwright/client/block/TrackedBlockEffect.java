package com.bluup.hexwright.client.block;

import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class TrackedBlockEffect {

    private final ResourceLocation fxId;
    private final @Nullable Vector3f offset;
    private final @Nullable Vector3f scale;
    private final Map<BlockPos, BlockEffect> active = new HashMap<>();

    public TrackedBlockEffect(ResourceLocation fxId, @Nullable Vector3f offset, @Nullable Vector3f scale) {
        this.fxId = fxId;
        this.offset = offset;
        this.scale = scale;
    }

    public void setActive(Level level, BlockPos pos, boolean shouldPlay) {
        if (!shouldPlay) {
            destroy(active.remove(pos));
            return;
        }
        BlockEffect existing = active.get(pos);
        if (existing != null) {
            FXRuntime runtime = existing.getRuntime();
            if (runtime != null && runtime.isAlive()) {
                return;
            }
            active.remove(pos);
        }
        BlockEffect effect = start(level, pos);
        if (effect != null) {
            active.put(pos, effect);
        }
    }

    public @Nullable BlockEffect start(Level level, BlockPos pos) {
        FX fx = FXHelper.getFX(fxId);
        if (fx == null) {
            return null;
        }
        try {
            BlockEffect effect = new BlockEffect(fx, level, pos);
            if (offset != null) {
                effect.setOffset(offset);
            }
            if (scale != null) {
                effect.setScale(scale);
            }
            effect.start();
            return effect;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void forgetAll() {
        active.clear();
    }

    public static void destroy(@Nullable BlockEffect effect) {
        if (effect == null) {
            return;
        }
        try {
            FXRuntime runtime = effect.getRuntime();
            if (runtime != null && runtime.isAlive()) {
                runtime.destroy(true);
            }
        } catch (RuntimeException ignored) {
        }
    }
}
