package com.bluup.hexwright.client.render;

import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class SodiumCompat {
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sodium");

    private SodiumCompat() {
    }

    public static void markSpriteActive(TextureAtlasSprite sprite) {
        if (INSTALLED) {
            SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
    }
}
