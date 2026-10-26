package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.item.HexwrightCreativeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;

public final class InstancedDungeons {

    public static final DungeonKeyItem DUNGEON_KEY = new DungeonKeyItem(
        new Item.Properties().stacksTo(16).rarity(Rarity.RARE), List.of(DungeonInstance.Kind.DUNGEON));

    public static final DungeonKeyItem CONSTRUCT_EXPERIMENT_KEY = new DungeonKeyItem(
        new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
        List.of(DungeonInstance.Kind.TALUS_ARENA, DungeonInstance.Kind.TOWER_SKELETON_ARENA,
            DungeonInstance.Kind.MAGNUS_ARENA, DungeonInstance.Kind.COG_SENTINEL_ARENA));

    public static final TravelStoneBlock TRAVEL_STONE = new TravelStoneBlock(travelStoneSettings());

    public static final TravelStonePartBlock TRAVEL_STONE_PART = new TravelStonePartBlock(travelStoneSettings()
        .pushReaction(PushReaction.BLOCK));

    public static final CrystalAltarBlock CRYSTAL_ALTAR = new CrystalAltarBlock(
        FabricBlockSettings.copyOf(Blocks.STONE).noOcclusion().strength(-1.0F, 3600000.0F).noLootTable()
            .lightLevel(state -> 7));

    public static BlockEntityType<TravelStoneBlockEntity> TRAVEL_STONE_BLOCK_ENTITY;

    public static BlockEntityType<TravelStonePartBlockEntity> TRAVEL_STONE_PART_BLOCK_ENTITY;

    public static BlockEntityType<CrystalAltarBlockEntity> CRYSTAL_ALTAR_BLOCK_ENTITY;

    private InstancedDungeons() {
    }

    static BlockBehaviour.Properties travelStoneSettings() {
        return FabricBlockSettings.copyOf(Blocks.STONE)
            .noOcclusion()
            .dynamicShape()
            .isViewBlocking((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .isValidSpawn((state, level, pos, type) -> false)
            .strength(-1.0F, 3600000.0F)
            .noLootTable();
    }

    public static ItemStack keyFor(DungeonInstance.Kind kind) {
        return new ItemStack(kind == DungeonInstance.Kind.DUNGEON ? DUNGEON_KEY : CONSTRUCT_EXPERIMENT_KEY);
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("dungeon_key"), DUNGEON_KEY);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("construct_experiment_key"),
            CONSTRUCT_EXPERIMENT_KEY);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("travel_stone"), TRAVEL_STONE);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("travel_stone_part"), TRAVEL_STONE_PART);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("crystal_altar"), CRYSTAL_ALTAR);
        Item travelStone = Registry.register(BuiltInRegistries.ITEM, Hexwright.id("travel_stone"),
            new BlockItem(TRAVEL_STONE, new Item.Properties().rarity(Rarity.EPIC)));
        Item crystalAltar = Registry.register(BuiltInRegistries.ITEM, Hexwright.id("crystal_altar"),
            new BlockItem(CRYSTAL_ALTAR, new Item.Properties().rarity(Rarity.RARE)));
        TRAVEL_STONE_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hexwright.id("travel_stone"),
            FabricBlockEntityTypeBuilder.create(TravelStoneBlockEntity::new, TRAVEL_STONE).build());
        TRAVEL_STONE_PART_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("travel_stone_part"),
            FabricBlockEntityTypeBuilder.create(TravelStonePartBlockEntity::new, TRAVEL_STONE_PART).build());
        CRYSTAL_ALTAR_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("crystal_altar"),
            FabricBlockEntityTypeBuilder.create(CrystalAltarBlockEntity::new, CRYSTAL_ALTAR).build());
        HexwrightCreativeTab.addTo(CreativeModeTabs.TOOLS_AND_UTILITIES, entries -> {
            entries.accept(DUNGEON_KEY);
            entries.accept(CONSTRUCT_EXPERIMENT_KEY);
            entries.accept(travelStone);
            entries.accept(crystalAltar);
        });
        AncientPortals.register();
        DungeonInstances.register();
        TravelStones.register();
        DungeonInstanceCommand.register();
    }
}
