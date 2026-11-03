package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum NourishmentSounds {
    DRINK("nourishment_drink", SoundEvents.HONEY_DRINK),
    PLANT("nourishment_plant", SoundEvents.AXE_STRIP),
    GROW("nourishment_grow", SoundEvents.ROOTS_PLACE),
    DARKEN("nourishment_darken", SoundEvents.SCULK_CATALYST_BLOOM),
    HEARTBEAT("nourishment_heartbeat", SoundEvents.WARDEN_HEARTBEAT),
    BURST("nourishment_burst", SoundEvents.SLIME_SQUISH),
    REBORN("nourishment_reborn", SoundEvents.TOTEM_USE),
    WITHER("nourishment_wither", SoundEvents.ROOTS_BREAK);

    private final ResourceLocation id;
    private SoundEvent event;

    NourishmentSounds(String path, SoundEvent fallback) {
        this.id = Hexwright.id(path);
        this.event = fallback;
    }

    public SoundEvent get() {
        return this.event;
    }

    public static void register() {
        for (NourishmentSounds sound : values()) {
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
