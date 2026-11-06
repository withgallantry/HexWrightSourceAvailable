package com.bluup.hexwright.server.maintenance;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class KeystoneStudy {

    public static final MaintenanceCrystalBlock MAINTENANCE_CRYSTAL = new MaintenanceCrystalBlock(
        FabricBlockSettings.copyOf(Blocks.AMETHYST_BLOCK)
            .strength(-1.0F, 3600000.0F)
            .noOcclusion()
            .noLootTable()
            .lightLevel(state -> state.getValue(MaintenanceCrystalBlock.ENGAGED) ? 15 : 8));

    public static BlockEntityType<MaintenanceCrystalBlockEntity> MAINTENANCE_CRYSTAL_BLOCK_ENTITY;

    public static final ResourceKey<PaintingVariant> PLANAR_KEYSTONE = painting("planar_keystone");
    public static final ResourceKey<PaintingVariant> ACTIVATION_ORDER = painting("activation_order");

    private KeystoneStudy() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("maintenance_crystal"), MAINTENANCE_CRYSTAL);
        MAINTENANCE_CRYSTAL_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("maintenance_crystal"),
            FabricBlockEntityTypeBuilder.create(MaintenanceCrystalBlockEntity::new, MAINTENANCE_CRYSTAL).build());
        Registry.register(BuiltInRegistries.PAINTING_VARIANT, PLANAR_KEYSTONE, new PaintingVariant(64, 48));
        Registry.register(BuiltInRegistries.PAINTING_VARIANT, ACTIVATION_ORDER, new PaintingVariant(32, 64));
        MaintenanceCommand.register();
        PlanarIncursion.register();
        WardenTokens.register();
    }

    private static ResourceKey<PaintingVariant> painting(String name) {
        return ResourceKey.create(Registries.PAINTING_VARIANT, Hexwright.id(name));
    }
}
