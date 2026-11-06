package com.bluup.hexwright.client.hexahedron;

import com.bluup.hexwright.server.hexahedron.Hexahedron;
import com.bluup.hexwright.server.hexahedron.HexahedronSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class HexahedronHum extends AbstractTickableSoundInstance {

    private static final double FADE_OUTSIDE = 96.0;
    private static final double PLACED_AT = 8.0;
    private static final float DRONE_VOLUME = 0.9f;
    private static final float PULSE_VOLUME = 1.0f;
    private static final float[] PULSE_PITCHES = {1.0f, 0.891f, 0.749f, 0.707f};
    private static final float EASE = 0.1f;

    private static final Map<UUID, HexahedronHum> PLAYING = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private final UUID hexahedronId;
    private float loudness;

    private HexahedronHum(Hexahedron hexahedron, Vec3 listener) {
        super(HexahedronSounds.DRONE.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.hexahedronId = hexahedron.id();
        this.looping = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0f;
        moveTo(placed(hexahedron, listener));
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                return;
            }
            Vec3 listener = listener();
            SoundManager sounds = client.getSoundManager();
            for (ClientHexahedra.Shown shown : ClientHexahedra.shown(client.level, 0.0f)) {
                Hexahedron hexahedron = shown.hexahedron();
                if (audibility(hexahedron, listener) <= 0.0f) {
                    continue;
                }
                HexahedronHum playing = PLAYING.get(hexahedron.id());
                if (playing != null && sounds.isActive(playing)) {
                    continue;
                }
                HexahedronHum hum = new HexahedronHum(hexahedron, listener);
                PLAYING.put(hexahedron.id(), hum);
                sounds.play(hum);
            }
        });
    }

    static void pulse(Hexahedron hexahedron) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 listener = listener();
        float volume = audibility(hexahedron, listener) * PULSE_VOLUME;
        if (volume <= 0.0f || minecraft.level == null) {
            return;
        }
        volume *= hexahedron.emergence(minecraft.level.getGameTime());
        Vec3 at = placed(hexahedron, listener);
        minecraft.getSoundManager().play(new SimpleSoundInstance(
            HexahedronSounds.PULSE.get().getLocation(), SoundSource.AMBIENT, volume,
            PULSE_PITCHES[RANDOM.nextInt(PULSE_PITCHES.length)], RANDOM, false, 0,
            SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Vec3 listener = listener();
        ClientHexahedra.Shown shown = shown();
        float wanted = 0.0f;
        if (shown != null) {
            moveTo(placed(shown.hexahedron(), listener));
            double now = Minecraft.getInstance().level == null ? 0.0 : Minecraft.getInstance().level.getGameTime();
            wanted = audibility(shown.hexahedron(), listener) * shown.emergence() * (1.0f - shown.hexahedron().cleared(now));
        }
        loudness += (wanted - loudness) * EASE;
        volume = loudness * DRONE_VOLUME;
        if (wanted == 0.0f && loudness < 0.01f) {
            PLAYING.remove(hexahedronId, this);
            stop();
        }
    }

    private @Nullable ClientHexahedra.Shown shown() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        for (ClientHexahedra.Shown shown : ClientHexahedra.shown(minecraft.level, 0.0f)) {
            if (shown.hexahedron().id().equals(hexahedronId)) {
                return shown;
            }
        }
        return null;
    }

    private void moveTo(Vec3 point) {
        x = point.x;
        y = point.y;
        z = point.z;
    }

    private static float audibility(Hexahedron hexahedron, Vec3 listener) {
        double across = Math.hypot(listener.x - hexahedron.x(), listener.z - hexahedron.z());
        if (across <= hexahedron.radius()) {
            return (float) (0.8 + 0.2 * (1.0 - across / hexahedron.radius()));
        }
        return (float) Math.max(0.0, 0.8 * (1.0 - (across - hexahedron.radius()) / FADE_OUTSIDE));
    }

    private static Vec3 placed(Hexahedron hexahedron, Vec3 listener) {
        Vec3 towards = hexahedron.anchor().subtract(0.0, Hexahedron.HALF_DIAGONAL / 2.0, 0.0).subtract(listener);
        return listener.add(towards.normalize().scale(PLACED_AT));
    }

    private static Vec3 listener() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }
}
