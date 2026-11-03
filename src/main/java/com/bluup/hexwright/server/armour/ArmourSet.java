package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.Hexwright;
import net.minecraft.resources.ResourceLocation;

public enum ArmourSet {
    VEILWALKER("veilwalker"),
    CANTOR("cantor"),
    HEXWARDEN("hexwarden"),
    AUGUR("augur"),
    VENATOR("venator", "venator_crystal"),
    DOMITOR("domitor");

    private final String id;
    private final String gemId;

    ArmourSet(String id) {
        this(id, id + "_gem");
    }

    ArmourSet(String id, String gemId) {
        this.id = id;
        this.gemId = gemId;
    }

    public String id() {
        return id;
    }

    public String gemId() {
        return gemId;
    }

    public String pieceId(ArmourTier tier, ArmourPiece piece) {
        return id + "_" + tier.id() + "_" + piece.id();
    }

    public ResourceLocation geo() {
        return Hexwright.id("geo/armour/" + id + ".geo.json");
    }

    public ResourceLocation texture(ArmourTier tier) {
        return Hexwright.id("textures/armour/" + id + "_" + tier.id() + ".png");
    }

    public String gemTitleKey() {
        return "item.hexwright." + gemId();
    }

    public String gemNameKey() {
        return gemTitleKey() + ".named";
    }
}
