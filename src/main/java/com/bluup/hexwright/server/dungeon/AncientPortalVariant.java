package com.bluup.hexwright.server.dungeon;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public enum AncientPortalVariant {
    DESERT("desert"),
    VOID("void"),
    MAGMA("magma"),
    MOSSY("mossy"),
    STONE("stone"),
    TERRACOTTA("terracotta");

    private final String id;
    private final AncientPortalBlock portal;
    private final AncientAltarBlock altar;
    private final BlockItem portalItem;
    private final AncientTabletItem tablet;

    AncientPortalVariant(String id) {
        this.id = id;
        this.portal = new AncientPortalBlock(this, InstancedDungeons.travelStoneSettings());
        this.altar = new AncientAltarBlock(this, AncientPortals.altarSettings());
        this.portalItem = new BlockItem(this.portal, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        this.tablet = new AncientTabletItem(this, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    public String id() {
        return this.id;
    }

    public AncientPortalBlock portal() {
        return this.portal;
    }

    public AncientAltarBlock altar() {
        return this.altar;
    }

    public BlockItem portalItem() {
        return this.portalItem;
    }

    public AncientTabletItem tablet() {
        return this.tablet;
    }

    public String recipeKey() {
        return "block.hexwright.ancient_portal_" + this.id;
    }
}
