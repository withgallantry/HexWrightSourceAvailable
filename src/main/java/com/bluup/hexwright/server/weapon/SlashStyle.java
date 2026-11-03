package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.resources.ResourceLocation;

public enum SlashStyle {

    PALE("slash", "slash-2", 1.0f, 1.0f),

    EMBER("red-slash", "red-slash-2", 1.0f, 1.0f);

    private final ResourceLocation crescent;
    private final ResourceLocation wave;
    private final float crescentBloom;
    private final float waveBloom;

    SlashStyle(String crescent, String wave, float crescentBloom, float waveBloom) {
        this.crescent = Hexwright.id("textures/fx/" + crescent + ".png");
        this.wave = Hexwright.id("textures/fx/" + wave + ".png");
        this.crescentBloom = crescentBloom;
        this.waveBloom = waveBloom;
    }

    public ResourceLocation crescentTexture() {
        return this.crescent;
    }

    public ResourceLocation waveTexture() {
        return this.wave;
    }

    public float crescentBloom() {
        return this.crescentBloom;
    }

    public float waveBloom() {
        return this.waveBloom;
    }

    public static SlashStyle byOrdinal(int ordinal) {
        SlashStyle[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PALE;
    }
}
