package com.bluup.hexwright.client.wardingbox;

import com.bluup.hexwright.client.block.TrackedBlockEffect;
import com.bluup.hexwright.Hexwright;
import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.ShapeSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class WardingBoxHitVisualClient {

    private static final ResourceLocation WARD_HIT_FX = Hexwright.id("ward_hitfx");

    private static final float FX_AUTHORED_SIZE = 5.0f;

    private static final float FX_Y_OFFSET = -0.5f;

    private static final Map<BlockPos, BlockEffect> ACTIVE = new HashMap<>();

    private static final Map<Long, FX> SHAPED = new HashMap<>();

    private static @Nullable FX shapedBase;

    private static boolean warnedMissingFx;

    private WardingBoxHitVisualClient() {
    }

    public static void onParticlesCleared() {
        ACTIVE.clear();
    }

    public static void handleTrigger(BlockPos pos, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        pruneDead();
        stop(pos);

        FX base = FXHelper.getFX(WARD_HIT_FX);
        if (base == null) {
            if (!warnedMissingFx) {
                warnedMissingFx = true;
                Hexwright.LOGGER.warn("Warding Box hit effect {} failed to load", WARD_HIT_FX);
            }
            return;
        }

        try {
            BlockEffect effect = new BlockEffect(shapedFor(base, width, height), level, pos);
            effect.setOffset(new Vector3f(0.0f, FX_Y_OFFSET, 0.0f));
            effect.start();
            ACTIVE.put(pos.immutable(), effect);
        } catch (RuntimeException e) {
            Hexwright.LOGGER.warn("Warding Box hit effect failed to play at {}", pos, e);
        }
    }

    private static FX shapedFor(FX base, int width, int height) {
        if (shapedBase != base) {
            SHAPED.clear();
            shapedBase = base;
        }
        long key = ((long) width << 32) | (height & 0xFFFFFFFFL);
        return SHAPED.computeIfAbsent(key, ignored -> {
            FX copy = new FX();
            copy.deserializeNBT(base.serializeNBT());
            copy.setFxLocation(base.getFxLocation());
            fitToVolume(copy.getMainFX().objects(), width, height);
            return copy;
        });
    }

    private static void fitToVolume(List<IFXObject> objects, int width, int height) {
        float fx = width / FX_AUTHORED_SIZE;
        float fy = height / FX_AUTHORED_SIZE;
        float dx = width - FX_AUTHORED_SIZE;
        float dy = height - FX_AUTHORED_SIZE;

        for (IFXObject object : objects) {
            if (!(object instanceof ParticleEmitter emitter)) {
                continue;
            }

            Vector3f local = emitter.transform().localPosition();
            emitter.transform().localPosition(new Vector3f(local.x * fx, local.y * fy, local.z * fx));

            ShapeSetting shape = emitter.config.shape;
            shape.setScale(grown(shape.getScale(), dx, dy, dx));
            shape.setPosition(scaled(shape.getPosition(), fx, fy, fx));
        }
    }

    private static NumberFunction3 scaled(NumberFunction3 source, float fx, float fy, float fz) {
        return new NumberFunction3(scaled(source.x, fx), scaled(source.y, fy), scaled(source.z, fz));
    }

    private static NumberFunction scaled(NumberFunction source, float factor) {
        if (source instanceof Constant constant) {
            return new Constant(constant.getNumber().floatValue() * factor);
        }
        return source;
    }

    private static NumberFunction3 grown(NumberFunction3 source, float dx, float dy, float dz) {
        return new NumberFunction3(grown(source.x, dx), grown(source.y, dy), grown(source.z, dz));
    }

    private static NumberFunction grown(NumberFunction source, float delta) {
        if (source instanceof Constant constant) {
            return new Constant(Math.max(0.0f, constant.getNumber().floatValue() + delta));
        }
        return source;
    }

    private static void stop(BlockPos pos) {
        BlockEffect effect = ACTIVE.remove(pos);
        if (effect == null) {
            return;
        }
        TrackedBlockEffect.destroy(effect);
    }

    private static void pruneDead() {
        Iterator<Map.Entry<BlockPos, BlockEffect>> it = ACTIVE.entrySet().iterator();
        Level level = Minecraft.getInstance().level;
        while (it.hasNext()) {
            BlockEffect effect = it.next().getValue();
            FXRuntime runtime = effect.getRuntime();
            if (runtime == null || !runtime.isAlive()) {
                it.remove();
            } else if (effect.getLevel() != level) {
                TrackedBlockEffect.destroy(effect);
                it.remove();
            }
        }
    }
}
