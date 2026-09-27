package com.bluup.hexwright.client.cloak;

import com.bluup.hexwright.server.cloak.CloakField;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class CloakFieldHum extends AbstractTickableSoundInstance {

    private static final double FULL_RANGE = 3.0;
    private static final double SILENT_RANGE = 8.0;
    private static final float MAX_VOLUME = 1.0f;
    private static final float EASE = 0.25f;

    private static final Map<UUID, CloakFieldHum> PLAYING = new HashMap<>();

    private final UUID fieldId;
    private float loudness;

    private CloakFieldHum(CloakField field, Vec3 listener) {
        super(HexwrightSoundEvents.cloakFieldHum(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.fieldId = field.id();
        this.looping = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0f;
        moveTo(field.surfacePoint(listener));
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                return;
            }
            Vec3 listener = listener();
            SoundManager sounds = client.getSoundManager();
            for (CloakField field : ClientCloakFields.fields()) {
                if (distanceToWall(field, listener) >= SILENT_RANGE) {
                    continue;
                }
                CloakFieldHum playing = PLAYING.get(field.id());
                if (playing != null && sounds.isActive(playing)) {
                    continue;
                }
                CloakFieldHum hum = new CloakFieldHum(field, listener);
                PLAYING.put(field.id(), hum);
                sounds.play(hum);
            }
        });
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Vec3 listener = listener();
        CloakField field = field();
        float wanted = 0.0f;
        if (field != null) {
            Vec3 wall = field.surfacePoint(listener);
            moveTo(wall);
            wanted = audibility(listener.distanceTo(wall));
        }
        loudness += (wanted - loudness) * EASE;
        volume = loudness * MAX_VOLUME;
        if (wanted == 0.0f && loudness < 0.01f) {
            PLAYING.remove(fieldId, this);
            stop();
        }
    }

    private @Nullable CloakField field() {
        for (CloakField field : ClientCloakFields.fields()) {
            if (field.id().equals(fieldId)) {
                return field;
            }
        }
        return null;
    }

    private void moveTo(Vec3 point) {
        x = point.x;
        y = point.y;
        z = point.z;
    }

    private static float audibility(double distance) {
        if (distance <= FULL_RANGE) {
            return 1.0f;
        }
        return (float) Math.max(0.0, 1.0 - (distance - FULL_RANGE) / (SILENT_RANGE - FULL_RANGE));
    }

    private static double distanceToWall(CloakField field, Vec3 listener) {
        return listener.distanceTo(field.surfacePoint(listener));
    }

    private static Vec3 listener() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }
}
