package com.bluup.hexwright.server.weapon;

public record WeaponSlash(String clip, float scale, float forward, float height,
                          int leadTicks, int lifeTicks, SlashStyle style) {

    public WeaponSlash withStyle(SlashStyle style) {
        return new WeaponSlash(this.clip, this.scale, this.forward, this.height, this.leadTicks,
            this.lifeTicks, style);
    }
}
