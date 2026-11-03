package com.bluup.hexwright.server.flourish;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.flourish.Flourish;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.EnumMap;
import java.util.Map;

public final class FlourishSounds {

    private static final Map<Flourish, SoundEvent> EVENTS = new EnumMap<>(Flourish.class);

    private FlourishSounds() {
    }

    public static SoundEvent get(Flourish flourish) {
        return EVENTS.getOrDefault(flourish, SoundEvents.AMETHYST_BLOCK_CHIME);
    }

    public static void register() {
        for (Flourish flourish : Flourish.values()) {
            ResourceLocation id = Hexwright.id(flourish.itemId());
            SoundEvent existing = BuiltInRegistries.SOUND_EVENT.get(id);
            if (existing != null) {
                EVENTS.put(flourish, existing);
                continue;
            }
            try {
                EVENTS.put(flourish, Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                    SoundEvent.createVariableRangeEvent(id)));
            } catch (IllegalStateException ex) {
                Hexwright.LOGGER.error("Failed to register {} before registry freeze; using fallback.", id, ex);
            }
        }
    }
}
