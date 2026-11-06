package com.bluup.hexwright.server.hexahedron;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum HexahedronSounds {
    DRONE("hexahedron_drone", SoundEvents.BEACON_AMBIENT),
    PULSE("hexahedron_pulse", SoundEvents.CONDUIT_AMBIENT),
    OFFERING("hexahedron_offering", SoundEvents.AMETHYST_BLOCK_CHIME),
    ANCHOR_TEAR("hexahedron_anchor_tear", SoundEvents.WARDEN_SONIC_CHARGE),
    ANCHOR_IMPACT("hexahedron_anchor_impact", SoundEvents.GENERIC_EXPLODE),
    LURCH("hexahedron_lurch", SoundEvents.CONDUIT_DEACTIVATE),
    BURST("hexahedron_burst", SoundEvents.BEACON_DEACTIVATE);

    private final ResourceLocation id;
    private SoundEvent event;

    HexahedronSounds(String path, SoundEvent fallback) {
        this.id = Hexwright.id(path);
        this.event = fallback;
    }

    public SoundEvent get() {
        return this.event;
    }

    public static void register() {
        for (HexahedronSounds sound : values()) {
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
