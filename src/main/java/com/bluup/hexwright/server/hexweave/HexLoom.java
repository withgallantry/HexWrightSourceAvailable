package com.bluup.hexwright.server.hexweave;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.item.HexwrightCreativeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class HexLoom {

    public static final HexLoomBlock BLOCK = new HexLoomBlock(
        FabricBlockSettings.copyOf(Blocks.LOOM).strength(0.6f, 1.5f).nonOpaque());

    public static final Item ITEM = new BlockItem(BLOCK, new Item.Properties());

    public static final Item WOVEN_ORB = new WovenOrbItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    public static BlockEntityType<HexLoomBlockEntity> BLOCK_ENTITY;

    private HexLoom() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("hex_loom"), BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("hex_loom"), ITEM);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("woven_orb"), WOVEN_ORB);
        BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hexwright.id("hex_loom"),
            FabricBlockEntityTypeBuilder.create(HexLoomBlockEntity::new, BLOCK).build());
        HexwrightCreativeTab.addTo(CreativeModeTabs.FUNCTIONAL_BLOCKS, entries -> entries.accept(ITEM));
        HexwrightCreativeTab.addTo(CreativeModeTabs.TOOLS_AND_UTILITIES, entries -> entries.accept(WOVEN_ORB));
        LoomNetworking.registerServer();
        WovenOrbTriggers.register();
    }
}
