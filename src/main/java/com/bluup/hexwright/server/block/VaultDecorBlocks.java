package com.bluup.hexwright.server.block;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VaultDecorBlocks {

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    public static final Block ALABASTER_BRICKS = masonry("alabaster_bricks");
    public static final Block ALABASTER_COLUMN = column("alabaster_column");
    public static final Block LUNARIN_SILVER_BRICKS = masonry("lunarin_silver_bricks");
    public static final Block LUNARIN_KNIGHT_BRICKS = masonry("lunarin_knight_bricks");
    public static final Block FARLANDER_EXORITE_BRICKS = masonry("farlander_exorite_bricks");
    public static final Block FARLANDER_LAPIS_BRICKS = masonry("farlander_lapis_bricks");
    public static final Block FARLANDER_ZANITE_BRICKS = masonry("farlander_zanite_bricks");
    public static final Block RED_GRANITE_BRICKS = masonry("red_granite_bricks");
    public static final Block SMALL_STONE_TILES = masonry("small_stone_tiles");
    public static final Block MOSSY_SMALL_STONE_TILES = masonry("mossy_small_stone_tiles");
    public static final Block CARVED_SNAKING_STONE = masonry("carved_snaking_stone");
    public static final Block ESSENCE_LAMP = lamp("essence_lamp");

    public static final Block NETHER_LABORATORY_DOOR = door("nether_laboratory_door");

    private VaultDecorBlocks() {
    }

    private static FabricBlockSettings masonrySettings() {
        return FabricBlockSettings.copyOf(Blocks.STONE_BRICKS);
    }

    private static Block masonry(String id) {
        return add(id, new Block(masonrySettings()), false);
    }

    private static Block lamp(String id) {
        return add(id, new Block(masonrySettings().lightLevel(state -> 15)), false);
    }

    private static Block column(String id) {
        return add(id, new RotatedPillarBlock(masonrySettings()), false);
    }

    private static Block door(String id) {
        return add(id, new VaultDoorBlock(
            FabricBlockSettings.copyOf(Blocks.IRON_DOOR).strength(1.5f, 6.0f)), true);
    }

    private static Block add(String id, Block block, boolean doubleHigh) {
        BLOCKS.put(id, block);
        ITEMS.put(id, doubleHigh
            ? new DoubleHighBlockItem(block, new Item.Properties())
            : new BlockItem(block, new Item.Properties()));
        return block;
    }

    private static final class VaultDoorBlock extends DoorBlock {
        private VaultDoorBlock(BlockBehaviour.Properties settings) {
            super(settings, BlockSetType.STONE);
        }
    }

    public static void register() {
        BLOCKS.forEach((id, block) ->
            Registry.register(BuiltInRegistries.BLOCK, Hexwright.id(id), block));
        ITEMS.forEach((id, item) ->
            Registry.register(BuiltInRegistries.ITEM, Hexwright.id(id), item));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> {
            for (Item item : ITEMS.values()) {
                entries.accept(item);
            }
        });
    }
}
