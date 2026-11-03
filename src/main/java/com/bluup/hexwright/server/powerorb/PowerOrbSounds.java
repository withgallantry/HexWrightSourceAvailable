package com.bluup.hexwright.server.powerorb;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum PowerOrbSounds {
    SANCTUARY_SUMMON("sanctuary_summon", SoundEvents.ROOTED_DIRT_BREAK),
    SANCTUARY_HEAL("sanctuary_heal", SoundEvents.AMETHYST_BLOCK_CHIME),
    GUARDIAN_SUMMON("guardian_summon", SoundEvents.ILLUSIONER_CAST_SPELL),
    GUARDIAN_GUARD("guardian_guard", SoundEvents.ARMOR_EQUIP_IRON),
    GUARDIAN_BLOCK("guardian_block", SoundEvents.SHIELD_BLOCK),
    GUARDIAN_STRIKE_1("guardian_strike_1", SoundEvents.PLAYER_ATTACK_SWEEP),
    GUARDIAN_STRIKE_2("guardian_strike_2", SoundEvents.PLAYER_ATTACK_SWEEP),
    GUARDIAN_PIERCE("guardian_pierce", SoundEvents.PLAYER_ATTACK_STRONG);

    private final ResourceLocation id;
    private SoundEvent event;

    PowerOrbSounds(String path, SoundEvent fallback) {
        this.id = Hexwright.id(path);
        this.event = fallback;
    }

    public SoundEvent get() {
        return this.event;
    }

    public static void register() {
        for (PowerOrbSounds sound : values()) {
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
