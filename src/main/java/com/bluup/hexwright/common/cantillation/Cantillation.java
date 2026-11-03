package com.bluup.hexwright.common.cantillation;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

public enum Cantillation {
    CHIMES("chimes", Rarity.UNCOMMON),
    GLASS("glass", Rarity.UNCOMMON),
    REEDS("reeds", Rarity.UNCOMMON),
    TINES("tines", Rarity.UNCOMMON),
    TIMBER("timber", Rarity.UNCOMMON),
    BELLS("bells", Rarity.RARE),
    STRINGS("strings", Rarity.RARE),
    MUSIC_BOX("music_box", Rarity.RARE),
    SINGING_BOWL("singing_bowl", Rarity.RARE),
    CHOIR("choir", Rarity.EPIC),
    SPHERES("spheres", Rarity.EPIC),
    HOLLOW("hollow", Rarity.EPIC);

    private final String id;
    private final Rarity rarity;

    Cantillation(String id, Rarity rarity) {
        this.id = id;
        this.rarity = rarity;
    }

    public String id() {
        return id;
    }

    public Rarity rarity() {
        return rarity;
    }

    public String itemId() {
        return "cantillation_" + id;
    }

    public static int keyOf(HexPattern pattern) {
        return (pattern.anglesSignature().hashCode() * 0x9E3779B9) >>> 24;
    }

    public static @Nullable Cantillation byId(String id) {
        for (Cantillation cantillation : values()) {
            if (cantillation.id.equals(id)) {
                return cantillation;
            }
        }
        return null;
    }
}
