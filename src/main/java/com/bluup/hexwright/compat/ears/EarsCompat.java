package com.bluup.hexwright.compat.ears;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public final class EarsCompat {

    private static final String MOD_ID = "ears";

    private EarsCompat() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        EarsChestInhibitor.register();
    }
}
