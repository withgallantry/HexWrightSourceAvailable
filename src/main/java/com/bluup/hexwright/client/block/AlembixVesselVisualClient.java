package com.bluup.hexwright.client.block;

import com.bluup.hexwright.Hexwright;
import com.lowdragmc.lowdraglib.client.scene.ParticleManager;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Color;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class AlembixVesselVisualClient {

    private static final ResourceLocation ALEMBIX_FX = Hexwright.id("alembix");

    private static final String[] EMITTERS = {"tank1", "tank2", "tank3", "tank4"};

    public static final int NO_TINT = -1;

    private static final Map<BlockPos, Playing> ACTIVE = new HashMap<>();

    private static boolean warnedMissingFx;

    private static float debugLift;
    private static boolean debugDepthTest = true;
    private static boolean debugDepthMask = true;

    private static int debugGeneration;

    private record Playing(BlockEffect effect, int[] tints, int generation) {
    }

    private static final class VesselFxLevel extends DummyWorld {
        private final Level real;

        VesselFxLevel(Level real) {
            super(real);
            this.real = real;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return real.getBlockState(pos);
        }
    }

    private static final class Stage {
        final Level real;
        final VesselFxLevel fxLevel;

        final ParticleManager particles = new PhotonParticleManager();

        Stage(Level real) {
            this.real = real;
            this.fxLevel = new VesselFxLevel(real);
            fxLevel.setParticleManager(particles);
            particles.setLevel(fxLevel);
        }
    }

    private static @Nullable Stage stage;

    private AlembixVesselVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (stage != null) {
                stage.particles.tick();
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Stage current = stage;
            if (current != null && current.particles.getParticleAmount() > 0) {
                current.particles.render(context.matrixStack(), context.camera(), context.tickDelta());
            }
        });
    }

    public static void setInputs(Level level, BlockPos pos, int[] tints) {
        if (isAllEmpty(tints)) {
            stop(pos);
            return;
        }

        Playing playing = ACTIVE.get(pos);
        if (playing != null) {
            if (Arrays.equals(playing.tints(), tints)
                && playing.generation() == debugGeneration
                && isAlive(playing.effect())) {
                return;
            }
            stop(pos);
        }

        FX base = FXHelper.getFX(ALEMBIX_FX);
        if (base == null) {
            if (!warnedMissingFx) {
                warnedMissingFx = true;
                Hexwright.LOGGER.warn("Alembix vessel effect {} failed to load", ALEMBIX_FX);
            }
            return;
        }

        try {
            BlockEffect effect = new BlockEffect(tintedCopy(base, tints), stageFor(level).fxLevel, pos);
            if (debugLift != 0.0f) {
                effect.setOffset(new Vector3f(0.0f, debugLift, 0.0f));
            }
            effect.start();
            ACTIVE.put(pos.immutable(), new Playing(effect, tints.clone(), debugGeneration));
        } catch (RuntimeException e) {
            Hexwright.LOGGER.warn("Alembix vessel effect failed to play at {}", pos, e);
        }
    }

    private static Stage stageFor(Level level) {
        Stage current = stage;
        if (current == null || current.real != level) {
            dropStage();
            current = new Stage(level);
            stage = current;
        }
        return current;
    }

    private static void dropStage() {
        Stage current = stage;
        if (current != null) {
            current.particles.clearAllParticles();
        }
        stage = null;
    }

    public static void stop(BlockPos pos) {
        Playing playing = ACTIVE.remove(pos);
        if (playing != null) {
            TrackedBlockEffect.destroy(playing.effect());
        }
    }

    public static void onParticlesCleared() {
        ACTIVE.clear();
        dropStage();
    }

    private static FX tintedCopy(FX base, int[] tints) {
        FX copy = new FX();
        copy.deserializeNBT(base.serializeNBT());
        copy.setFxLocation(base.getFxLocation());
        applyTints(copy.getMainFX().objects(), tints);
        return copy;
    }

    private static void applyTints(List<IFXObject> objects, int[] tints) {
        for (Iterator<IFXObject> it = objects.iterator(); it.hasNext(); ) {
            IFXObject object = it.next();
            int index = emitterIndex(object.getName());
            if (index < 0) {
                continue;
            }
            int tint = index < tints.length ? tints[index] : NO_TINT;
            if (tint == NO_TINT) {
                it.remove();
            } else if (object instanceof ParticleEmitter emitter) {
                emitter.config.setStartColor(new Color(0xFF000000 | (tint & 0xFFFFFF)));
                emitter.config.material.setDepthMask(debugDepthMask);
                if (!debugDepthTest) {
                    emitter.config.material.setDepthTest(false);
                }
            }
        }
    }

    private static int emitterIndex(@Nullable String name) {
        for (int i = 0; i < EMITTERS.length; i++) {
            if (EMITTERS[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAllEmpty(int[] tints) {
        for (int tint : tints) {
            if (tint != NO_TINT) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAlive(BlockEffect effect) {
        var runtime = effect.getRuntime();
        return runtime != null && runtime.isAlive();
    }


    static void setDebugLift(float blocks) {
        debugLift = blocks;
        debugGeneration++;
    }

    static float debugLift() {
        return debugLift;
    }

    static void setDebugDepthTest(boolean enabled) {
        debugDepthTest = enabled;
        debugGeneration++;
    }

    static boolean debugDepthTest() {
        return debugDepthTest;
    }

    static void setDebugDepthMask(boolean enabled) {
        debugDepthMask = enabled;
        debugGeneration++;
    }

    static boolean debugDepthMask() {
        return debugDepthMask;
    }

    static boolean isFxLoadable() {
        return FXHelper.getFX(ALEMBIX_FX) != null;
    }

    static int liveEmitterCount(BlockPos pos) {
        Playing playing = ACTIVE.get(pos);
        if (playing == null) {
            return -1;
        }
        var runtime = playing.effect().getRuntime();
        if (runtime == null || !runtime.isAlive()) {
            return -1;
        }
        return playing.tints().length - (int) Arrays.stream(playing.tints())
            .filter(tint -> tint == NO_TINT).count();
    }

    static int liveParticleCount() {
        Stage current = stage;
        return current == null ? 0 : current.particles.getParticleAmount();
    }
}
