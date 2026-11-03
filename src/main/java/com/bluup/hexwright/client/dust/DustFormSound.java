package com.bluup.hexwright.client.dust;

import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;

final class DustFormSound extends AbstractTickableSoundInstance {

    private static final float MAX_VOLUME = 0.8f;
    private static final float SETTLED_PITCH = 0.8f;
    private static final float PITCH_PER_PACE = 0.35f;
    private static final float MAX_PACE = 1.5f;
    private static final float EMPTY_PITCH = 0.7f;
    private static final float PITCH_PER_BUILD = 0.45f;
    private static final float ATTACK = 0.2f;
    private static final float DISSOLVE_ATTACK = 0.4f;
    private static final float RELEASE = 0.3f;
    private static final float GLIDE = 0.15f;
    private static final float LOOKS_BUILT = 0.8f;
    private static final float LOOKS_GONE = 0.15f;
    private static final int ORPHAN_TICKS = 5;

    private final DustConstruct construct;
    private float lastBuild;
    private float lastAge;
    private int staleTicks;
    private float loudness;

    private DustFormSound(DustConstruct construct) {
        super(HexwrightSoundEvents.dustForm(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.construct = construct;
        this.looping = true;
        this.volume = 0.0f;
        this.pitch = construct.dissolving ? EMPTY_PITCH + PITCH_PER_BUILD * construct.build : SETTLED_PITCH;
        this.lastBuild = construct.build;
        this.lastAge = construct.age;
        follow();
    }

    static void attach(DustConstruct construct) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (construct.sound != null && sounds.isActive(construct.sound)) {
            return;
        }
        construct.sound = new DustFormSound(construct);
        sounds.play(construct.sound);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        staleTicks = construct.age == lastAge ? staleTicks + 1 : 0;
        lastAge = construct.age;
        float change = construct.build - lastBuild;
        lastBuild = construct.build;

        float wanted;
        float targetPitch;
        float build = construct.build;
        if (construct.dissolving) {
            wanted = DustConstruct.smoothstep(LOOKS_GONE, LOOKS_GONE + 0.3f, build);
            targetPitch = EMPTY_PITCH + PITCH_PER_BUILD * build;
        } else {
            float pace = change * Math.max(DustConfig.constructBuildTicks, 1.0f);
            pace = Math.max(0.0f, Math.min(pace, MAX_PACE));
            wanted = Math.min(pace, 1.0f) * (1.0f - DustConstruct.smoothstep(LOOKS_BUILT - 0.15f, LOOKS_BUILT, build));
            targetPitch = SETTLED_PITCH + PITCH_PER_PACE * pace;
        }
        if (staleTicks > 0) {
            wanted = 0.0f;
        }
        wanted *= 0.4f + 0.6f * construct.coverage;

        float attack = construct.dissolving ? DISSOLVE_ATTACK : ATTACK;
        loudness += (wanted - loudness) * (wanted > loudness ? attack : RELEASE);
        volume = loudness * MAX_VOLUME;
        pitch += (targetPitch - pitch) * GLIDE;
        follow();

        boolean over = construct.dissolving ? build <= LOOKS_GONE : build >= LOOKS_BUILT;
        if (over ? loudness < 0.01f : staleTicks > ORPHAN_TICKS) {
            stop();
        }
    }

    private void follow() {
        x = construct.x;
        y = construct.y;
        z = construct.z;
    }
}
