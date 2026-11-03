package com.bluup.hexwright.common.malediction;

import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

public enum MaledictionFinish {
    BALEFUL("baleful", "baleful_tome", Rarity.UNCOMMON, 1.0F, 0xD447B5),
    SEARING("searing", "searing_tome", Rarity.UNCOMMON, 0.8F, 0xF07A1C),
    RIME("rime", "rime_tome", Rarity.UNCOMMON, 0.6F, 0x5FCDE4),
    CONCUSSIVE("concussive", "concussive_tome", Rarity.UNCOMMON, 0.8F, 0xB8C8D8),
    SUNDERING("sundering", "sundering_tome", Rarity.RARE, 0.5F, 0x4F6FD8),
    WITHERING("withering", "withering_tome", Rarity.RARE, 0.4F, 0x4FA85C),
    RADIANT("radiant", "radiant_tome", Rarity.RARE, 1.0F, 0xFFBF1F),
    GALVANIC("galvanic", "galvanic_tome", Rarity.RARE, 1.0F, 0xD4F23A),
    REAPING("reaping", "reaping_tome", Rarity.RARE, 1.0F, 0x12B39A),
    LEECHING("leeching", "leeching_tome", Rarity.EPIC, 0.5F, 0xD8344F),
    RESONANT("resonant", "resonant_tome", Rarity.EPIC, 0.7F, 0x9B5CC8);

    private final String id;
    private final String itemId;
    private final Rarity rarity;
    private final float damageScale;
    private final int colour;

    MaledictionFinish(String id, String itemId, Rarity rarity, float damageScale, int colour) {
        this.id = id;
        this.itemId = itemId;
        this.rarity = rarity;
        this.damageScale = damageScale;
        this.colour = colour;
    }

    public String id() {
        return id;
    }

    public String itemId() {
        return itemId;
    }

    public Rarity rarity() {
        return rarity;
    }

    public float damageScale() {
        return damageScale;
    }

    public int colour() {
        return colour;
    }

    public String nameKey() {
        return "malediction.hexwright.finish." + id;
    }

    public static @Nullable MaledictionFinish byId(String id) {
        for (MaledictionFinish finish : values()) {
            if (finish.id.equals(id)) {
                return finish;
            }
        }
        return null;
    }
}
