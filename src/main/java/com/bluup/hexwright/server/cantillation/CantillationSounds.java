package com.bluup.hexwright.server.cantillation;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.cantillation.Cantillation;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.EnumMap;
import java.util.Map;

public final class CantillationSounds {

    private static final Map<Cantillation, SoundEvent> NOTES = new EnumMap<>(Cantillation.class);
    private static final Map<Cantillation, SoundEvent> STROKES = new EnumMap<>(Cantillation.class);

    private CantillationSounds() {
    }

    public static SoundEvent get(Cantillation cantillation) {
        return NOTES.getOrDefault(cantillation, SoundEvents.AMETHYST_BLOCK_CHIME);
    }

    public static SoundEvent stroke(Cantillation cantillation) {
        return STROKES.getOrDefault(cantillation, SoundEvents.AMETHYST_BLOCK_HIT);
    }

    public static void register() {
        for (Cantillation cantillation : Cantillation.values()) {
            register(NOTES, cantillation, Hexwright.id(cantillation.itemId()));
            register(STROKES, cantillation, Hexwright.id(cantillation.itemId() + "_stroke"));
        }
    }

    private static void register(Map<Cantillation, SoundEvent> events, Cantillation cantillation, ResourceLocation id) {
        SoundEvent existing = BuiltInRegistries.SOUND_EVENT.get(id);
        if (existing != null) {
            events.put(cantillation, existing);
            return;
        }
        try {
            events.put(cantillation, Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createVariableRangeEvent(id)));
        } catch (IllegalStateException ex) {
            Hexwright.LOGGER.error("Failed to register {} before registry freeze; using fallback.", id, ex);
        }
    }
}
