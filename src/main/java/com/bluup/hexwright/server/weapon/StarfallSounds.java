package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum StarfallSounds {
    ARROW_SHOOT("starfall_arrow_shoot", SoundEvents.ARROW_SHOOT),
    ARROW_WHOOSH("starfall_arrow_whoosh", SoundEvents.TRIDENT_RIPTIDE_1),
    ARROW_GROUND("starfall_arrow_ground", SoundEvents.ARROW_HIT),
    HIT_THUD("starfall_hit_thud", SoundEvents.PLAYER_ATTACK_STRONG);

    private final ResourceLocation id;
    private SoundEvent event;

    StarfallSounds(String path, SoundEvent fallback) {
        this.id = Hexwright.id(path);
        this.event = fallback;
    }

    public SoundEvent get() {
        return this.event;
    }

    public static void register() {
        for (StarfallSounds sound : values()) {
            SoundEvent existing = BuiltInRegistries.SOUND_EVENT.get(sound.id);
            if (existing != null) {
                sound.event = existing;
                continue;
            }
            try {
                sound.event = Registry.register(
                    BuiltInRegistries.SOUND_EVENT,
                    sound.id,
                    SoundEvent.createVariableRangeEvent(sound.id));
            } catch (IllegalStateException ex) {
                Hexwright.LOGGER.error("Failed to register {} before registry freeze; using fallback.",
                    sound.id, ex);
            }
        }
    }
}
