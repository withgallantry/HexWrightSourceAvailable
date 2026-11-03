package com.bluup.hexwright.common.malediction;

import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

public enum MaledictionForm {
    BOLT("bolt", "bolt_scroll", Rarity.UNCOMMON, 6.0F, 3, 20, 1),
    NOVA("nova", "nova_scroll", Rarity.UNCOMMON, 7.0F, 2, 60, 2),
    GRASP("grasp", "grasp_scroll", Rarity.UNCOMMON, 8.0F, 3, 20, 1),
    LANCE("lance", "lance_scroll", Rarity.RARE, 6.0F, 2, 40, 2),
    ARC("arc", "arc_scroll", Rarity.RARE, 6.0F, 3, 30, 1),
    THROW("throw", "throw_scroll", Rarity.RARE, 7.0F, 2, 40, 2),
    FIELD("field", "field_scroll", Rarity.RARE, 7.0F, 2, 60, 2),
    SNARE("snare", "snare_scroll", Rarity.RARE, 8.0F, 2, 100, 2),
    CHAIN("chain", "chain_scroll", Rarity.RARE, 6.0F, 2, 40, 2),
    VOLLEY("volley", "volley_scroll", Rarity.RARE, 4.0F, 2, 40, 2),
    SEEKER("seeker", "seeker_scroll", Rarity.EPIC, 7.0F, 2, 60, 3),
    THREAD("thread", "thread_scroll", Rarity.EPIC, 6.0F, 3, 30, 2),
    SWEEP("sweep", "sweep_scroll", Rarity.EPIC, 4.0F, 2, 80, 3),
    CONJUNCTION("conjunction", "conjunction_scroll", Rarity.EPIC, 7.0F, 2, 60, 3),
    ORBIT("orbit", "orbit_scroll", Rarity.EPIC, 4.0F, 1, 200, 3),
    DOOM("doom", "doom_scroll", Rarity.EPIC, 10.0F, 2, 100, 3);

    private final String id;
    private final String itemId;
    private final Rarity rarity;
    private final float damage;
    private final int charges;
    private final int rechargeTicks;
    private final int dustCost;

    MaledictionForm(String id, String itemId, Rarity rarity, float damage, int charges, int rechargeTicks, int dustCost) {
        this.id = id;
        this.itemId = itemId;
        this.rarity = rarity;
        this.damage = damage;
        this.charges = charges;
        this.rechargeTicks = rechargeTicks;
        this.dustCost = dustCost;
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

    public float damage() {
        return damage;
    }

    public int charges() {
        return charges;
    }

    public int rechargeTicks() {
        return rechargeTicks;
    }

    public int dustCost() {
        return dustCost;
    }

    public String nameKey() {
        return "malediction.hexwright.form." + id;
    }

    public static @Nullable MaledictionForm byId(String id) {
        for (MaledictionForm form : values()) {
            if (form.id.equals(id)) {
                return form;
            }
        }
        return null;
    }
}
