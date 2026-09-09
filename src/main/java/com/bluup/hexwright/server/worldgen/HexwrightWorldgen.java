package com.bluup.hexwright.server.worldgen;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonPiece;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonStructure;
import com.bluup.hexwright.server.worldgen.arena.ArenaPiece;
import com.bluup.hexwright.server.worldgen.arena.ArenaStructure;
import com.bluup.hexwright.server.worldgen.bindstone.BindstonePillarPiece;
import com.bluup.hexwright.server.worldgen.bindstone.BindstonePillarStructure;
import com.bluup.hexwright.server.worldgen.ruinedportal.RuinedPortalPiece;
import com.bluup.hexwright.server.worldgen.ruinedportal.RuinedPortalStructure;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class HexwrightWorldgen {

    public static PlacementModifierType<NearLavaFilter> NEAR_LAVA;

    public static StructureType<DungeonStructure> DEEP_DUNGEON;

    public static StructurePieceType DUNGEON_PIECE;

    public static StructureType<ArenaStructure> ANCIENT_ARENA;

    public static StructurePieceType ARENA_PIECE;

    public static StructureType<RuinedPortalStructure> RUINED_PORTAL;

    public static StructurePieceType RUINED_PORTAL_PIECE;

    public static StructureType<BindstonePillarStructure> BINDSTONE_PILLAR;

    public static StructurePieceType BINDSTONE_PILLAR_PIECE;

    public static final ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> DEEP_DUNGEON_KEY =
        ResourceKey.create(Registries.STRUCTURE, Hexwright.id("deep_dungeon"));

    public static final ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> ANCIENT_ARENA_KEY =
        ResourceKey.create(Registries.STRUCTURE, Hexwright.id("ancient_arena"));

    private static final ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> CRYSTALITE_ORE =
        ResourceKey.create(Registries.PLACED_FEATURE, Hexwright.id("crystalite_ore"));

    private static final ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> CRYSTALITE_NETHER_ORE =
        ResourceKey.create(Registries.PLACED_FEATURE, Hexwright.id("crystalite_nether_ore"));

    private HexwrightWorldgen() {
    }

    public static void register() {
        NEAR_LAVA = Registry.register(
            BuiltInRegistries.PLACEMENT_MODIFIER_TYPE,
            Hexwright.id("near_lava"),
            () -> NearLavaFilter.CODEC
        );

        DEEP_DUNGEON = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            Hexwright.id("deep_dungeon"),
            () -> DungeonStructure.CODEC
        );
        DUNGEON_PIECE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Hexwright.id("deep_dungeon"),
            (StructurePieceType.StructureTemplateType) DungeonPiece::new
        );

        ANCIENT_ARENA = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            Hexwright.id("ancient_arena"),
            () -> ArenaStructure.CODEC
        );
        ARENA_PIECE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Hexwright.id("ancient_arena"),
            (StructurePieceType.StructureTemplateType) ArenaPiece::new
        );

        RUINED_PORTAL = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            Hexwright.id("ruined_portal"),
            () -> RuinedPortalStructure.CODEC
        );
        RUINED_PORTAL_PIECE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Hexwright.id("ruined_portal"),
            (StructurePieceType.ContextlessType) RuinedPortalPiece::new
        );

        BINDSTONE_PILLAR = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            Hexwright.id("bindstone_pillar"),
            () -> BindstonePillarStructure.CODEC
        );
        BINDSTONE_PILLAR_PIECE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Hexwright.id("bindstone_pillar"),
            (StructurePieceType.ContextlessType) BindstonePillarPiece::new
        );

        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            CRYSTALITE_ORE
        );
        BiomeModifications.addFeature(
            BiomeSelectors.foundInTheNether(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            CRYSTALITE_NETHER_ORE
        );
    }
}
