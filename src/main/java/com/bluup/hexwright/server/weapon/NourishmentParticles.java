package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class NourishmentParticles {

    public static final SimpleParticleType BLOOD_WISP = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Hexwright.id("blood_wisp"), FabricParticleTypes.simple());

    private NourishmentParticles() {
    }

    public static void register() {
    }
}
