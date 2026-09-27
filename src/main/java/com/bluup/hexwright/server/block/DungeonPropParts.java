package com.bluup.hexwright.server.block;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;

public final class DungeonPropParts {

    public static final DungeonPropPartBlock BLOCK = new DungeonPropPartBlock(
        FabricBlockSettings.copyOf(Blocks.STONE)
            .noOcclusion()
            .isViewBlocking((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .strength(0.6F, 1.0F)
            .noLootTable()
            .pushReaction(PushReaction.BLOCK));

    public static BlockEntityType<DungeonPropPartBlockEntity> BLOCK_ENTITY;

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("prop_part"), BLOCK);
        BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("dungeon_prop_part"),
            FabricBlockEntityTypeBuilder.create(DungeonPropPartBlockEntity::new, BLOCK).build());
    }

    private DungeonPropParts() {
    }
}
