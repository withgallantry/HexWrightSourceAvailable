package com.bluup.hexwright.compat.hexic;

import com.bluup.hexwright.server.region.HexpanseBridge;
import net.fabricmc.loader.api.FabricLoader;

public final class HexicCompat {

    private static final String MOD_ID = "hexic";

    private HexicCompat() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        HexicViews.register();
        if (!HexpanseBridge.isLoaded()) {
            RegionAbsorb.register();
        }
    }
}
