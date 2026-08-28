package com.bluup.hexwright.client.portal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;

import java.lang.reflect.Field;

public final class IrisParticlePhase {

    private static final Field PHASE_FIELD = resolve();

    private IrisParticlePhase() {
    }

    private static Field resolve() {
        Class<?> phaseType;
        try {
            phaseType = Class.forName("net.irisshaders.iris.fantastic.ParticleRenderingPhase");
        } catch (ClassNotFoundException absent) {
            return null;
        }
        for (Field field : ParticleEngine.class.getDeclaredFields()) {
            if (field.getType() == phaseType) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    public static Object save() {
        if (PHASE_FIELD == null) {
            return null;
        }
        try {
            return PHASE_FIELD.get(Minecraft.getInstance().particleEngine);
        } catch (IllegalAccessException impossible) {
            return null;
        }
    }

    public static void restore(Object phase) {
        if (PHASE_FIELD == null || phase == null) {
            return;
        }
        try {
            PHASE_FIELD.set(Minecraft.getInstance().particleEngine, phase);
        } catch (IllegalAccessException impossible) {
        }
    }
}
