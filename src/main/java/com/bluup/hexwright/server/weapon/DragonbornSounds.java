package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum DragonbornSounds {
    METEOR_CREATION("dragonborn_meteor_creation", SoundEvents.BLAZE_SHOOT),
    METEOR_SHOOT("dragonborn_meteor_shoot", SoundEvents.GHAST_SHOOT),
    METEOR_EXPLOSION("dragonborn_meteor_explosion", SoundEvents.GENERIC_EXPLODE),
    FIRE_EXPLODE("dragonborn_fire_explode", SoundEvents.FIRECHARGE_USE),
    RUPTURE("dragonborn_rupture", SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR);

    private final ResourceLocation id;
    private SoundEvent event;

    DragonbornSounds(String path, SoundEvent fallback) {
        this.id = Hexwright.id(path);
        this.event = fallback;
    }

    public SoundEvent get() {
        return this.event;
    }

    public static void register() {
        for (DragonbornSounds sound : values()) {
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
