package com.bluup.hexwright.compat.create;

import net.fabricmc.loader.api.FabricLoader;

public final class CreateCompat {

    private static final String MOD_ID = "create";

    private CreateCompat() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        CreateMovementChecks.register();
    }
}
