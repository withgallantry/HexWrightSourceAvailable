package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum SilverStarlightSounds {
    ICE_CHARGE("silver_starlight_ice_charge", SoundEvents.AMETHYST_BLOCK_CHIME),
    ICE_SPIKE("silver_starlight_ice_spike", SoundEvents.GLASS_PLACE),
    ICE_BREAK("silver_starlight_ice_break", SoundEvents.GLASS_BREAK);

    private final ResourceLocation id;
    private SoundEvent event;

    SilverStarlightSounds(String path, SoundEvent fallback) {
        this.id = Hexwright.id(path);
        this.event = fallback;
    }

    public SoundEvent get() {
        return this.event;
    }

    public static void register() {
        for (SilverStarlightSounds sound : values()) {
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
