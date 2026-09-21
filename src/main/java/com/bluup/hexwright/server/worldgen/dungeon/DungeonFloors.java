package com.bluup.hexwright.server.worldgen.dungeon;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

final class DungeonFloors {

    private DungeonFloors() {
    }

    static final List<Block> PAVEMENT = List.of(
        Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE,
        Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_BRICKS,
        Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
        Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
        Blocks.CHISELED_STONE_BRICKS, Blocks.BRICKS,
        Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.GRANITE, Blocks.POLISHED_GRANITE,
        Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.TUFF, Blocks.CALCITE,
        Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
        Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.OBSIDIAN, Blocks.NETHERRACK,
        Blocks.SANDSTONE, Blocks.RED_SANDSTONE,
        Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.GRAVEL,
        Blocks.SAND, Blocks.CLAY, Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.PACKED_MUD,
        Blocks.MUD_BRICKS,
        Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.BIRCH_PLANKS,
        Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG, Blocks.BIRCH_LOG,
        Blocks.TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA,
        Blocks.WHITE_TERRACOTTA, Blocks.GRAY_TERRACOTTA,
        Blocks.RED_WOOL, Blocks.GRAY_WOOL, Blocks.BLACK_WOOL, Blocks.WHITE_WOOL);

    static final List<Block> CLUTTER = List.of(
        Blocks.BOOKSHELF, Blocks.CHISELED_BOOKSHELF, Blocks.BARREL, Blocks.CRAFTING_TABLE,
        Blocks.NOTE_BLOCK, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LECTERN, Blocks.JUKEBOX,
        Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.ANVIL, Blocks.CAULDRON,
        Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.COMPOSTER, Blocks.SPAWNER,
        Blocks.DISPENSER, Blocks.FLOWER_POT, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
        Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.TORCH, Blocks.WALL_TORCH,
        Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH, Blocks.CANDLE, Blocks.GLOWSTONE,
        Blocks.SHROOMLIGHT, Blocks.SEA_LANTERN);
}
