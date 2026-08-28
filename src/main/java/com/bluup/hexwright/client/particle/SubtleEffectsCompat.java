package com.bluup.hexwright.client.particle;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.particle.Particle;

import java.lang.reflect.Method;

public final class SubtleEffectsCompat {

    private static final boolean PRESENT = detect();

    private static Method ignoresCulling;
    private static boolean unavailable;
    private static boolean reported;

    private SubtleEffectsCompat() {
    }

    public static boolean isPresent() {
        return PRESENT;
    }

    public static void exemptFromCulling(Particle particle) {
        if (!PRESENT || unavailable || particle == null) {
            return;
        }
        try {
            Method method = ignoresCulling;
            if (method == null) {
                method = Particle.class.getMethod("subtleEffects$ignoresCulling");
                ignoresCulling = method;
            }
            method.invoke(particle);
            if (!reported) {
                reported = true;
                Hexwright.LOGGER.info(
                    "Subtle Effects detected; exempting Photon emitters from its particle culling");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
            Hexwright.LOGGER.warn(
                "Subtle Effects is loaded but its particle-culling opt-out could not be called;"
                    + " Photon effects may be invisible. Work around it by setting"
                    + " enableParticleCulling = false in config/subtle_effects/general.toml",
                e);
        }
    }

    private static boolean detect() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("subtle_effects") || loader.isModLoaded("subtleeffects");
    }
}
