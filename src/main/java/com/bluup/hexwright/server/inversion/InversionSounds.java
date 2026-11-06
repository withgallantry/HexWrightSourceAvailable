package com.bluup.hexwright.server.inversion;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum InversionSounds {
    INVERSION_TEAR("inversion_tear", SoundEvents.WARDEN_SONIC_BOOM),
    INVERSION_RUMBLE("inversion_rumble", SoundEvents.WARDEN_DIG),
    INVERSION_DREAD("inversion_dread", SoundEvents.WARDEN_HEARTBEAT),
    INVERSION_RECEDE("inversion_recede", SoundEvents.BEACON_DEACTIVATE),
    ARBITER_EMERGE("arbiter_emerge", SoundEvents.WARDEN_EMERGE),
    ARBITER_SPIN("arbiter_spin", SoundEvents.ELYTRA_FLYING),
    ARBITER_SNAP("arbiter_snap", SoundEvents.RESPAWN_ANCHOR_CHARGE),
    ARBITER_CHARGE("arbiter_charge", SoundEvents.BEACON_POWER_SELECT),
    ARBITER_LASER("arbiter_laser", SoundEvents.WARDEN_SONIC_BOOM),
    ARBITER_FACE("arbiter_face", SoundEvents.SCULK_SHRIEKER_SHRIEK),
    ARBITER_ANSWER("arbiter_answer", SoundEvents.AMETHYST_BLOCK_CHIME),
    ARBITER_OPEN("arbiter_open", SoundEvents.BEACON_ACTIVATE),
    ARBITER_SHARD_BREAK("arbiter_shard_break", SoundEvents.GLASS_BREAK);

    private final ResourceLocation id;
    private SoundEvent event;

    InversionSounds(String path, SoundEvent fallback) {
        this.id = Hexwright.id(path);
        this.event = fallback;
    }

    public SoundEvent get() {
        return this.event;
    }

    public static void register() {
        for (InversionSounds sound : values()) {
            SoundEvent existing = BuiltInRegistries.SOUND_EVENT.get(sound.id);
            if (existing != null) {
                sound.event = existing;
                continue;
            }
            try {
                sound.event = Registry.register(BuiltInRegistries.SOUND_EVENT, sound.id,
                    SoundEvent.createVariableRangeEvent(sound.id));
            } catch (IllegalStateException ex) {
                Hexwright.LOGGER.error("Failed to register {} before registry freeze; using fallback.", sound.id, ex);
            }
        }
    }
}
