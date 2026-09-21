package com.bluup.hexwright.server.block;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.item.WardingBoxItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class HexwrightBlocks {
    private static final float HARDNESS = 0.6f;
    private static final float BLAST_RESISTANCE = 1.5f;

    private static FabricBlockSettings soft(Block base) {
        return FabricBlockSettings.copyOf(base).strength(HARDNESS, BLAST_RESISTANCE);
    }

    public static final StaffAssemblyBlock STAFF_ASSEMBLY_BLOCK = new StaffAssemblyBlock(
        soft(Blocks.SMITHING_TABLE)
            .noOcclusion()
    );

    public static final Item STAFF_ASSEMBLY_ITEM = new BlockItem(
        STAFF_ASSEMBLY_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<StaffAssemblyBlockEntity> STAFF_ASSEMBLY_BLOCK_ENTITY;

    public static final CrucibleBlock CRUCIBLE_BLOCK = new CrucibleBlock(
        soft(Blocks.CAULDRON)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 13 : 0)
    );

    public static final Item CRUCIBLE_ITEM = new BlockItem(
        CRUCIBLE_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<CrucibleBlockEntity> CRUCIBLE_BLOCK_ENTITY;

    public static final WorktableBlock WORKTABLE_BLOCK = new WorktableBlock(
        soft(Blocks.FLETCHING_TABLE)
            .noOcclusion()
    );

    public static final Item WORKTABLE_ITEM = new BlockItem(
        WORKTABLE_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<WorktableBlockEntity> WORKTABLE_BLOCK_ENTITY;

    public static final WardingBoxBlock WARDING_BOX_BLOCK = new WardingBoxBlock(
        soft(Blocks.LODESTONE)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 7 : 0)
            .nonOpaque()
    );

    public static final Item WARDING_BOX_ITEM = new WardingBoxItem(
        WARDING_BOX_BLOCK,
        new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
    );

    public static BlockEntityType<WardingBoxBlockEntity> WARDING_BOX_BLOCK_ENTITY;

    public static final Block CRYSTALITE_ORE_BLOCK = new DropExperienceBlock(
        FabricBlockSettings.copyOf(Blocks.DEEPSLATE_GOLD_ORE),
        UniformInt.of(2, 5)
    );

    public static final Item CRYSTALITE_ORE_ITEM = new BlockItem(
        CRYSTALITE_ORE_BLOCK,
        new Item.Properties()
    );

    public static final Block CRYSTALITE_NETHER_ORE_BLOCK = new DropExperienceBlock(
        FabricBlockSettings.copyOf(Blocks.NETHER_GOLD_ORE),
        UniformInt.of(2, 5)
    );

    public static final Item CRYSTALITE_NETHER_ORE_ITEM = new BlockItem(
        CRYSTALITE_NETHER_ORE_BLOCK,
        new Item.Properties()
    );

    public static final CoalescerBlock COALESCER_BLOCK = new CoalescerBlock(
        soft(Blocks.SMOKER)
            .luminance(state -> 0)
            .nonOpaque()
    );

    public static final Item COALESCER_ITEM = new BlockItem(
        COALESCER_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<CoalescerBlockEntity> COALESCER_BLOCK_ENTITY;

    public static final ResonanceTowerBlock RESONANCE_TOWER_BLOCK = new ResonanceTowerBlock(
        soft(Blocks.AMETHYST_BLOCK)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 10 : 0)
            .nonOpaque()
    );

    public static final Item RESONANCE_TOWER_ITEM = new BlockItem(
        RESONANCE_TOWER_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<ResonanceTowerBlockEntity> RESONANCE_TOWER_BLOCK_ENTITY;

    public static final EssenceGaugeBlock ESSENCE_GAUGE_BLOCK = new EssenceGaugeBlock(
        soft(Blocks.DAYLIGHT_DETECTOR)
    );

    public static final Item ESSENCE_GAUGE_ITEM = new BlockItem(
        ESSENCE_GAUGE_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<EssenceGaugeBlockEntity> ESSENCE_GAUGE_BLOCK_ENTITY;

    public static final ReliquaryBlock RELIQUARY_BLOCK = new ReliquaryBlock(
        soft(Blocks.DEEPSLATE_BRICKS)
    );

    public static final Item RELIQUARY_ITEM = new BlockItem(
        RELIQUARY_BLOCK,
        new Item.Properties()
    );

    public static final ReliquaryBlock MANIFOLD_VAULT_BLOCK = new ReliquaryBlock(
        soft(Blocks.DEEPSLATE_BRICKS).noOcclusion()
    );

    public static final Item MANIFOLD_VAULT_ITEM = new BlockItem(
        MANIFOLD_VAULT_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<ReliquaryBlockEntity> RELIQUARY_BLOCK_ENTITY;

    public static final ReliquaryMirrorBlock RELIQUARY_MIRROR_BLOCK = new ReliquaryMirrorBlock(
        soft(Blocks.CHEST)
    );

    public static final Item RELIQUARY_MIRROR_ITEM = new BlockItem(
        RELIQUARY_MIRROR_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<ReliquaryMirrorBlockEntity> RELIQUARY_MIRROR_BLOCK_ENTITY;

    public static final LeywellBlock LEYWELL_BLOCK = new LeywellBlock(
        soft(Blocks.AMETHYST_BLOCK)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 7 : 0)
            .noOcclusion()
    );

    public static final Item LEYWELL_ITEM = new BlockItem(
        LEYWELL_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<LeywellBlockEntity> LEYWELL_BLOCK_ENTITY;

    public static final ResonantAnchorBlock RESONANT_ANCHOR_BLOCK = new ResonantAnchorBlock(
        soft(Blocks.RESPAWN_ANCHOR)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 7 : 0)
    );

    public static final Item RESONANT_ANCHOR_ITEM = new BlockItem(
        RESONANT_ANCHOR_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<ResonantAnchorBlockEntity> RESONANT_ANCHOR_BLOCK_ENTITY;

    public static final FieldMarkerBlock FIELD_MARKER_BLOCK = new FieldMarkerBlock(
        soft(Blocks.REDSTONE_LAMP)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 9 : 0)
    );

    public static final Item FIELD_MARKER_ITEM = new BlockItem(
        FIELD_MARKER_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<FieldMarkerBlockEntity> FIELD_MARKER_BLOCK_ENTITY;

    public static final HarmonicExchangeBlock HARMONIC_EXCHANGE_BLOCK = new HarmonicExchangeBlock(
        soft(Blocks.AMETHYST_BLOCK)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 9 : 0)
            .nonOpaque()
    );

    public static final Item HARMONIC_EXCHANGE_ITEM = new BlockItem(
        HARMONIC_EXCHANGE_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<HarmonicExchangeBlockEntity> HARMONIC_EXCHANGE_BLOCK_ENTITY;

    public static final HarmonicEmitterBlock HARMONIC_EMITTER_BLOCK = new HarmonicEmitterBlock(
        soft(Blocks.AMETHYST_BLOCK)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 7 : 0)
            .nonOpaque()
    );

    public static final Item HARMONIC_EMITTER_ITEM = new BlockItem(
        HARMONIC_EMITTER_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<HarmonicEmitterBlockEntity> HARMONIC_EMITTER_BLOCK_ENTITY;

    public static final HarmonicTransducerBlock HARMONIC_TRANSDUCER_BLOCK = new HarmonicTransducerBlock(
        soft(Blocks.AMETHYST_BLOCK)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 5 : 0)
            .nonOpaque()
    );

    public static final Item HARMONIC_TRANSDUCER_ITEM = new BlockItem(
        HARMONIC_TRANSDUCER_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<HarmonicTransducerBlockEntity> HARMONIC_TRANSDUCER_BLOCK_ENTITY;

    public static final ExchangeBridgeBlock EXCHANGE_BRIDGE_BLOCK = new ExchangeBridgeBlock(
        soft(Blocks.AMETHYST_BLOCK)
            .luminance(state -> state.getValue(HexwrightBlockStates.ACTIVE) ? 6 : 0)
            .nonOpaque()
    );

    public static final Item EXCHANGE_BRIDGE_ITEM = new BlockItem(
        EXCHANGE_BRIDGE_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<ExchangeBridgeBlockEntity> EXCHANGE_BRIDGE_BLOCK_ENTITY;

    private static Block vaultShell(Block appearance) {
        return new Block(FabricBlockSettings.copyOf(appearance)
            .strength(-1.0f, 3600000.0f)
            .noLootTable());
    }

    public static final BindstoneBlock BINDSTONE_BLOCK = new BindstoneBlock(
        FabricBlockSettings.copyOf(Blocks.DEEPSLATE_BRICKS)
            .luminance(state -> state.getValue(BindstoneBlock.RUNE) != 0 ? 6 : 0)
    );

    public static final Item BINDSTONE_ITEM = new BlockItem(
        BINDSTONE_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<BindstoneCoreBlockEntity> BINDSTONE_CORE_BLOCK_ENTITY;

    public static BlockEntityType<BindstoneCubeBlockEntity> BINDSTONE_CUBE_BLOCK_ENTITY;

    public static final Block BINDSTONE_COLUMN_BLOCK = new Block(
        FabricBlockSettings.copyOf(Blocks.DEEPSLATE_BRICKS)
    );

    public static final Item BINDSTONE_COLUMN_ITEM = new BlockItem(
        BINDSTONE_COLUMN_BLOCK,
        new Item.Properties()
    );

    private static final class BindstoneStairBlock extends StairBlock {
        private BindstoneStairBlock(BlockState base, FabricBlockSettings settings) {
            super(base, settings);
        }
    }

    public static final Block BINDSTONE_STAIRS_BLOCK = new BindstoneStairBlock(
        Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
        FabricBlockSettings.copyOf(Blocks.DEEPSLATE_BRICK_STAIRS)
    );

    public static final Item BINDSTONE_STAIRS_ITEM = new BlockItem(
        BINDSTONE_STAIRS_BLOCK,
        new Item.Properties()
    );

    public static final Block BINDSTONE_SLAB_BLOCK = new SlabBlock(
        FabricBlockSettings.copyOf(Blocks.DEEPSLATE_BRICK_SLAB)
    );

    public static final Item BINDSTONE_SLAB_ITEM = new BlockItem(
        BINDSTONE_SLAB_BLOCK,
        new Item.Properties()
    );

    public static final Block VAULT_SHELL_BLOCK = vaultShell(Blocks.POLISHED_BLACKSTONE_BRICKS);
    public static final Block VAULT_FLOOR_BLOCK = vaultShell(Blocks.POLISHED_BLACKSTONE);
    public static final Block VAULT_LAMP_BLOCK = vaultShell(Blocks.SEA_LANTERN);
    public static final Block VAULT_FRAME_BLOCK = vaultShell(Blocks.AMETHYST_BLOCK);

    public static final Block REFINED_BINDSTONE_BLOCK = new Block(
        FabricBlockSettings.copyOf(Blocks.STONE)
            .strength(-1.0f, 3600000.0f)
            .noLootTable()
    );

    public static final RuinedPortalFrameBlock RUINED_PORTAL_FRAME_BLOCK = new RuinedPortalFrameBlock(
        FabricBlockSettings.copyOf(Blocks.STONE)
            .noOcclusion()
            .noCollission()
            .strength(-1.0f, 3600000.0f)
            .noLootTable()
    );

    public static BlockEntityType<RuinedPortalFrameBlockEntity> RUINED_PORTAL_FRAME_BLOCK_ENTITY;

    public static final VaultPlinthBlock VAULT_PLINTH_BLOCK = new VaultPlinthBlock(
        FabricBlockSettings.copyOf(Blocks.CHISELED_QUARTZ_BLOCK)
            .strength(-1.0f, 3600000.0f)
            .noOcclusion()
            .noLootTable()
    );

    public static BlockEntityType<VaultPlinthBlockEntity> VAULT_PLINTH_BLOCK_ENTITY;

    public static final DecadentVaultExitBlock DECADENT_VAULT_EXIT_BLOCK = new DecadentVaultExitBlock(
        FabricBlockSettings.copyOf(Blocks.AMETHYST_BLOCK)
            .luminance(state -> 8)
            .strength(-1.0f, 3600000.0f)
            .noLootTable()
    );

    public static BlockEntityType<DecadentVaultExitBlockEntity> DECADENT_VAULT_EXIT_BLOCK_ENTITY;

    public static final PlacedBottleBlock PLACED_BOTTLE_BLOCK = new PlacedBottleBlock(
        FabricBlockSettings.copyOf(Blocks.FLOWER_POT)
            .sounds(SoundType.GLASS)
            .strength(0.2f)
            .noOcclusion()
            .noLootTable()
    );

    public static BlockEntityType<PlacedBottleBlockEntity> PLACED_BOTTLE_BLOCK_ENTITY;

    public static final com.bluup.hexwright.server.fluid.HexidTankBlock HEXID_TANK_BLOCK =
        new com.bluup.hexwright.server.fluid.HexidTankBlock(
            soft(Blocks.COPPER_BLOCK)
                .noOcclusion()
        );

    public static final Item HEXID_TANK_ITEM = new BlockItem(
        HEXID_TANK_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<com.bluup.hexwright.server.fluid.HexidTankBlockEntity> HEXID_TANK_BLOCK_ENTITY;

    public static final com.bluup.hexwright.server.fluid.HexidPipeBlock HEXID_PIPE_BLOCK =
        new com.bluup.hexwright.server.fluid.HexidPipeBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .sound(SoundType.COPPER)
                .strength(0.0f, BLAST_RESISTANCE)
                .noOcclusion()
        );

    public static final Item HEXID_PIPE_ITEM = new BlockItem(
        HEXID_PIPE_BLOCK,
        new Item.Properties()
    );

    public static final AlembixBlock ALEMBIX_BLOCK = new AlembixBlock(
        soft(Blocks.COPPER_BLOCK)
            .noOcclusion()
    );

    public static final Item ALEMBIX_ITEM = new BlockItem(
        ALEMBIX_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<AlembixBlockEntity> ALEMBIX_BLOCK_ENTITY;

    public static final com.bluup.hexwright.server.fluid.LiquefactriumBlock LIQUEFACTRIUM_BLOCK =
        new com.bluup.hexwright.server.fluid.LiquefactriumBlock(
            soft(Blocks.COPPER_BLOCK)
                .noOcclusion()
        );

    public static final Item LIQUEFACTRIUM_ITEM = new BlockItem(
        LIQUEFACTRIUM_BLOCK,
        new Item.Properties()
    );

    public static BlockEntityType<com.bluup.hexwright.server.fluid.LiquefactriumBlockEntity>
        LIQUEFACTRIUM_BLOCK_ENTITY;

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("staff_assembly"), STAFF_ASSEMBLY_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("staff_assembly"), STAFF_ASSEMBLY_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("crucible"), CRUCIBLE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("crucible"), CRUCIBLE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("worktable"), WORKTABLE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("worktable"), WORKTABLE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("warding_box"), WARDING_BOX_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("warding_box"), WARDING_BOX_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("crystalite_ore"), CRYSTALITE_ORE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("crystalite_ore"), CRYSTALITE_ORE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("crystalite_nether_ore"), CRYSTALITE_NETHER_ORE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("crystalite_nether_ore"), CRYSTALITE_NETHER_ORE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("coalescer"), COALESCER_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("coalescer"), COALESCER_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("resonance_tower"), RESONANCE_TOWER_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("resonance_tower"), RESONANCE_TOWER_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("essence_gauge"), ESSENCE_GAUGE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("essence_gauge"), ESSENCE_GAUGE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("reliquary"), RELIQUARY_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("reliquary"), RELIQUARY_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("manifold_vault"), MANIFOLD_VAULT_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("manifold_vault"), MANIFOLD_VAULT_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("reliquary_mirror"), RELIQUARY_MIRROR_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("reliquary_mirror"), RELIQUARY_MIRROR_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("leywell"), LEYWELL_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("leywell"), LEYWELL_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("resonant_anchor"), RESONANT_ANCHOR_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("resonant_anchor"), RESONANT_ANCHOR_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("field_marker"), FIELD_MARKER_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("field_marker"), FIELD_MARKER_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("harmonic_exchange"), HARMONIC_EXCHANGE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("harmonic_exchange"), HARMONIC_EXCHANGE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("harmonic_emitter"), HARMONIC_EMITTER_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("harmonic_emitter"), HARMONIC_EMITTER_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("harmonic_transducer"), HARMONIC_TRANSDUCER_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("harmonic_transducer"), HARMONIC_TRANSDUCER_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("exchange_bridge"), EXCHANGE_BRIDGE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("exchange_bridge"), EXCHANGE_BRIDGE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("bindstone"), BINDSTONE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("bindstone"), BINDSTONE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("bindstone_column"), BINDSTONE_COLUMN_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("bindstone_column"), BINDSTONE_COLUMN_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("bindstone_stairs"), BINDSTONE_STAIRS_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("bindstone_stairs"), BINDSTONE_STAIRS_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("bindstone_slab"), BINDSTONE_SLAB_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("bindstone_slab"), BINDSTONE_SLAB_ITEM);

        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("vault_shell"), VAULT_SHELL_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("vault_floor"), VAULT_FLOOR_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("vault_lamp"), VAULT_LAMP_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("vault_frame"), VAULT_FRAME_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("refined_bindstone"), REFINED_BINDSTONE_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("ruined_portal_frame"), RUINED_PORTAL_FRAME_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("vault_plinth"), VAULT_PLINTH_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("decadent_vault_exit"), DECADENT_VAULT_EXIT_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("placed_bottle"), PLACED_BOTTLE_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("hexid_tank"), HEXID_TANK_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("hexid_tank"), HEXID_TANK_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("hexid_pipe"), HEXID_PIPE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("hexid_pipe"), HEXID_PIPE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("alembix"), ALEMBIX_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("alembix"), ALEMBIX_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("liquefactrium"), LIQUEFACTRIUM_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("liquefactrium"), LIQUEFACTRIUM_ITEM);

        VaultDecorBlocks.register();

        DungeonProp.register();

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(STAFF_ASSEMBLY_ITEM);
            entries.accept(CRUCIBLE_ITEM);
            entries.accept(WORKTABLE_ITEM);
            entries.accept(WARDING_BOX_ITEM);
            entries.accept(COALESCER_ITEM);
            entries.accept(RESONANCE_TOWER_ITEM);
            entries.accept(ESSENCE_GAUGE_ITEM);
            entries.accept(MANIFOLD_VAULT_ITEM);
            entries.accept(RELIQUARY_MIRROR_ITEM);
            entries.accept(LEYWELL_ITEM);
            entries.accept(RESONANT_ANCHOR_ITEM);
            entries.accept(FIELD_MARKER_ITEM);
            entries.accept(HARMONIC_EXCHANGE_ITEM);
            entries.accept(HARMONIC_EMITTER_ITEM);
            entries.accept(HARMONIC_TRANSDUCER_ITEM);
            entries.accept(EXCHANGE_BRIDGE_ITEM);
            entries.accept(HEXID_TANK_ITEM);
            entries.accept(HEXID_PIPE_ITEM);
            entries.accept(ALEMBIX_ITEM);
            entries.accept(LIQUEFACTRIUM_ITEM);
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(entries -> {
            entries.accept(CRYSTALITE_ORE_ITEM);
            entries.accept(CRYSTALITE_NETHER_ORE_ITEM);
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> {
            entries.accept(BINDSTONE_ITEM);
            entries.accept(BINDSTONE_COLUMN_ITEM);
            entries.accept(BINDSTONE_STAIRS_ITEM);
            entries.accept(BINDSTONE_SLAB_ITEM);
        });

        STAFF_ASSEMBLY_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("staff_assembly"),
            FabricBlockEntityTypeBuilder.create(StaffAssemblyBlockEntity::new, STAFF_ASSEMBLY_BLOCK).build()
        );

        CRUCIBLE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("crucible"),
            FabricBlockEntityTypeBuilder.create(CrucibleBlockEntity::new, CRUCIBLE_BLOCK).build()
        );

        WORKTABLE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("worktable"),
            FabricBlockEntityTypeBuilder.create(WorktableBlockEntity::new, WORKTABLE_BLOCK).build()
        );

        WARDING_BOX_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("warding_box"),
            FabricBlockEntityTypeBuilder.create(WardingBoxBlockEntity::new, WARDING_BOX_BLOCK).build()
        );

        COALESCER_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("coalescer"),
            FabricBlockEntityTypeBuilder.create(CoalescerBlockEntity::new, COALESCER_BLOCK).build()
        );

        RESONANCE_TOWER_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("resonance_tower"),
            FabricBlockEntityTypeBuilder.create(ResonanceTowerBlockEntity::new, RESONANCE_TOWER_BLOCK).build()
        );

        ESSENCE_GAUGE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("essence_gauge"),
            FabricBlockEntityTypeBuilder.create(EssenceGaugeBlockEntity::new, ESSENCE_GAUGE_BLOCK).build()
        );

        RELIQUARY_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("reliquary"),
            FabricBlockEntityTypeBuilder.create(ReliquaryBlockEntity::new, RELIQUARY_BLOCK, MANIFOLD_VAULT_BLOCK).build()
        );

        RELIQUARY_MIRROR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("reliquary_mirror"),
            FabricBlockEntityTypeBuilder.create(ReliquaryMirrorBlockEntity::new, RELIQUARY_MIRROR_BLOCK).build()
        );

        LEYWELL_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("leywell"),
            FabricBlockEntityTypeBuilder.create(LeywellBlockEntity::new, LEYWELL_BLOCK).build()
        );

        RESONANT_ANCHOR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("resonant_anchor"),
            FabricBlockEntityTypeBuilder.create(ResonantAnchorBlockEntity::new, RESONANT_ANCHOR_BLOCK).build()
        );

        FIELD_MARKER_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("field_marker"),
            FabricBlockEntityTypeBuilder.create(FieldMarkerBlockEntity::new, FIELD_MARKER_BLOCK).build()
        );

        HARMONIC_EXCHANGE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("harmonic_exchange"),
            FabricBlockEntityTypeBuilder.create(HarmonicExchangeBlockEntity::new, HARMONIC_EXCHANGE_BLOCK).build()
        );

        HARMONIC_EMITTER_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("harmonic_emitter"),
            FabricBlockEntityTypeBuilder.create(HarmonicEmitterBlockEntity::new, HARMONIC_EMITTER_BLOCK).build()
        );

        HARMONIC_TRANSDUCER_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("harmonic_transducer"),
            FabricBlockEntityTypeBuilder.create(HarmonicTransducerBlockEntity::new, HARMONIC_TRANSDUCER_BLOCK).build()
        );

        EXCHANGE_BRIDGE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("exchange_bridge"),
            FabricBlockEntityTypeBuilder.create(ExchangeBridgeBlockEntity::new, EXCHANGE_BRIDGE_BLOCK).build()
        );

        BINDSTONE_CORE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("bindstone_core"),
            FabricBlockEntityTypeBuilder.create(BindstoneCoreBlockEntity::new, BINDSTONE_BLOCK).build()
        );

        BINDSTONE_CUBE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("bindstone_cube"),
            FabricBlockEntityTypeBuilder.create(BindstoneCubeBlockEntity::new, BINDSTONE_BLOCK).build()
        );

        RUINED_PORTAL_FRAME_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("ruined_portal_frame"),
            FabricBlockEntityTypeBuilder.create(RuinedPortalFrameBlockEntity::new, RUINED_PORTAL_FRAME_BLOCK).build()
        );

        VAULT_PLINTH_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("vault_plinth"),
            FabricBlockEntityTypeBuilder.create(VaultPlinthBlockEntity::new, VAULT_PLINTH_BLOCK).build()
        );

        PLACED_BOTTLE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("placed_bottle"),
            FabricBlockEntityTypeBuilder.create(PlacedBottleBlockEntity::new, PLACED_BOTTLE_BLOCK).build()
        );

        DECADENT_VAULT_EXIT_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("decadent_vault_exit"),
            FabricBlockEntityTypeBuilder.create(DecadentVaultExitBlockEntity::new, DECADENT_VAULT_EXIT_BLOCK).build()
        );

        HEXID_TANK_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("hexid_tank"),
            FabricBlockEntityTypeBuilder.create(
                com.bluup.hexwright.server.fluid.HexidTankBlockEntity::new, HEXID_TANK_BLOCK).build()
        );

        ALEMBIX_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("alembix"),
            FabricBlockEntityTypeBuilder.create(AlembixBlockEntity::new, ALEMBIX_BLOCK).build()
        );

        LIQUEFACTRIUM_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("liquefactrium"),
            FabricBlockEntityTypeBuilder.create(
                com.bluup.hexwright.server.fluid.LiquefactriumBlockEntity::new,
                LIQUEFACTRIUM_BLOCK).build()
        );
    }

    private HexwrightBlocks() {
    }
}
