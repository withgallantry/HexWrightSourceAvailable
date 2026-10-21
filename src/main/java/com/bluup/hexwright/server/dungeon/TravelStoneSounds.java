package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum TravelStoneSounds {
    OPENING("travel_stone_opening", SoundEvents.BEACON_ACTIVATE),
    IDLE("travel_stone_idle", SoundEvents.BEACON_AMBIENT);

    private final ResourceLocation id;
    private SoundEvent event;

    TravelStoneSounds(String path, SoundEvent fallback) {
        this.id = Hexwright.id(path);
        this.event = fallback;
    }

    public SoundEvent get() {
        return this.event;
    }

    public static void register() {
        for (TravelStoneSounds sound : values()) {
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
