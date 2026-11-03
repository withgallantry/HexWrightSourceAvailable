package com.bluup.hexwright.server.armour;

import net.minecraft.world.item.ArmorItem;

public enum ArmourPiece {
    HELMET("helmet", ArmorItem.Type.HELMET),
    CHESTPLATE("chestplate", ArmorItem.Type.CHESTPLATE),
    LEGGINGS("leggings", ArmorItem.Type.LEGGINGS),
    BOOTS("boots", ArmorItem.Type.BOOTS);

    private final String id;
    private final ArmorItem.Type type;

    ArmourPiece(String id, ArmorItem.Type type) {
        this.id = id;
        this.type = type;
    }

    public String id() {
        return id;
    }

    public ArmorItem.Type type() {
        return type;
    }
}
