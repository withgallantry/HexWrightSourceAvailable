package com.bluup.hexwright;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Hexwright {
    public static final String MOD_ID = "hexwright";
    public static final Logger LOGGER = LoggerFactory.getLogger("Hexwright");

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private Hexwright() {
    }
}
