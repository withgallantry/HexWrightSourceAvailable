package com.bluup.hexwright.client.inversion;

import com.bluup.hexwright.server.inversion.Inversion;
import com.bluup.hexwright.server.inversion.InversionSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class InversionDread extends AbstractTickableSoundInstance {

    private static final double FADE_OUTSIDE = 160.0;
    private static final double PLACED_AT = 8.0;
    private static final float VOLUME = 0.85f;
    private static final double OPEN_FRONT = 80.0;
    private static final float EASE = 0.05f;

    private static final Map<UUID, InversionDread> PLAYING = new HashMap<>();

    private final UUID inversionId;
    private float loudness;

    private InversionDread(Inversion inversion, ClientLevel level, Vec3 listener) {
        super(InversionSounds.INVERSION_DREAD.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.inversionId = inversion.id();
        this.looping = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0f;
        moveTo(placed(inversion, level, listener));
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                return;
            }
            Vec3 listener = listener();
            SoundManager sounds = client.getSoundManager();
            for (Inversion inversion : ClientInversions.in(client.level)) {
                if (wanted(inversion, client.level, listener) <= 0.0f) {
                    continue;
                }
                InversionDread playing = PLAYING.get(inversion.id());
                if (playing != null && sounds.isActive(playing)) {
                    continue;
                }
                InversionDread dread = new InversionDread(inversion, client.level, listener);
                PLAYING.put(inversion.id(), dread);
                sounds.play(dread);
            }
        });
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        Inversion inversion = level == null ? null : find(level);
        Vec3 listener = listener();
        float wanted = 0.0f;
        if (inversion != null) {
            moveTo(placed(inversion, level, listener));
            wanted = wanted(inversion, level, listener);
        }
        loudness += (wanted - loudness) * EASE;
        volume = loudness * VOLUME;
        if (wanted == 0.0f && loudness < 0.01f) {
            PLAYING.remove(inversionId, this);
            stop();
        }
    }

    private @Nullable Inversion find(ClientLevel level) {
        for (Inversion inversion : ClientInversions.in(level)) {
            if (inversion.id().equals(inversionId)) {
                return inversion;
            }
        }
        return null;
    }

    private static float wanted(Inversion inversion, ClientLevel level, Vec3 listener) {
        double outside = Math.hypot(listener.x - inversion.x(), listener.z - inversion.z()) - Inversion.HALF;
        float near = 1.0f - (float) Mth.clamp(outside / FADE_OUTSIDE, 0.0, 1.0);
        return near * (float) Mth.clamp(inversion.front(level.getGameTime()) / OPEN_FRONT, 0.0, 1.0);
    }

    private void moveTo(Vec3 point) {
        x = point.x;
        y = point.y;
        z = point.z;
    }

    private static Vec3 placed(Inversion inversion, ClientLevel level, Vec3 listener) {
        Vec3 rent = new Vec3(inversion.x(), inversion.rentY(level.getMaxBuildHeight()), inversion.z());
        return listener.add(rent.subtract(listener).normalize().scale(PLACED_AT));
    }

    private static Vec3 listener() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }
}
