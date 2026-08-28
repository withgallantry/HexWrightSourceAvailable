package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.resources.ResourceLocation;

public enum SlashStyle {

    PALE("slash", "slash-2"),

    EMBER("red-slash", "red-slash-2");

    private final ResourceLocation crescent;
    private final ResourceLocation wave;

    SlashStyle(String crescent, String wave) {
        this.crescent = Hexwright.id("textures/fx/" + crescent + ".png");
        this.wave = Hexwright.id("textures/fx/" + wave + ".png");
    }

    public ResourceLocation crescentTexture() {
        return this.crescent;
    }

    public ResourceLocation waveTexture() {
        return this.wave;
    }

    public static SlashStyle byOrdinal(int ordinal) {
        SlashStyle[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PALE;
    }
}
