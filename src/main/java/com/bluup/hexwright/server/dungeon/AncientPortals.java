package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.item.HexwrightCreativeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public final class AncientPortals {

    public static BlockEntityType<AncientPortalBlockEntity> PORTAL_BLOCK_ENTITY;

    public static BlockEntityType<AncientAltarBlockEntity> ALTAR_BLOCK_ENTITY;

    private AncientPortals() {
    }

    static BlockBehaviour.Properties altarSettings() {
        return FabricBlockSettings.copyOf(Blocks.STONE).noOcclusion().strength(-1.0F, 3600000.0F).noLootTable();
    }

    public static List<Item> unlisted() {
        List<Item> items = new ArrayList<>();
        for (AncientPortalVariant variant : AncientPortalVariant.values()) {
            items.add(variant.portalItem());
            items.add(variant.tablet());
        }
        return items;
    }

    static void register() {
        for (AncientPortalVariant variant : AncientPortalVariant.values()) {
            Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("ancient_portal_" + variant.id()), variant.portal());
            Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("ancient_altar_" + variant.id()), variant.altar());
            Registry.register(BuiltInRegistries.ITEM, Hexwright.id("ancient_portal_" + variant.id()),
                variant.portalItem());
            Registry.register(BuiltInRegistries.ITEM, Hexwright.id("ancient_tablet_" + variant.id()), variant.tablet());
        }
        PORTAL_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hexwright.id("ancient_portal"),
            FabricBlockEntityTypeBuilder.create(AncientPortalBlockEntity::new, blocks(AncientPortalVariant::portal)).build());
        ALTAR_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hexwright.id("ancient_altar"),
            FabricBlockEntityTypeBuilder.create(AncientAltarBlockEntity::new, blocks(AncientPortalVariant::altar)).build());
        Registry.register(BuiltInRegistries.LOOT_POOL_ENTRY_TYPE, Hexwright.id("ancient_tablet"), AncientTabletLoot.TYPE);
        HexwrightCreativeTab.addTo(CreativeModeTabs.TOOLS_AND_UTILITIES, entries -> {
            for (AncientPortalVariant variant : AncientPortalVariant.values()) {
                entries.accept(variant.portalItem());
                entries.accept(variant.tablet());
            }
        });
    }

    private static Block[] blocks(Function<AncientPortalVariant, Block> block) {
        return Arrays.stream(AncientPortalVariant.values()).map(block).toArray(Block[]::new);
    }
}
