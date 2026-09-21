package com.bluup.hexwright.server.mob;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class HexwrightMobEntities {

    public static final EntityType<ExperimentalConstructEntity> EXPERIMENTAL_CONSTRUCT = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("experimental_construct"),
        FabricEntityTypeBuilder.<ExperimentalConstructEntity>create(MobCategory.MONSTER, ExperimentalConstructEntity::new)
            .dimensions(EntityDimensions.scalable(1.25f, 1.9f))
            .trackRangeBlocks(48)
            .trackedUpdateRate(3)
            .build()
    );

    public static final EntityType<ServitorConstructEntity> SERVITOR_CONSTRUCT = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("servitor_construct"),
        FabricEntityTypeBuilder.<ServitorConstructEntity>create(MobCategory.MONSTER, ServitorConstructEntity::new)
            .dimensions(EntityDimensions.scalable(1.05f, 2.35f))
            .trackRangeBlocks(48)
            .trackedUpdateRate(3)
            .build()
    );

    public static final EntityType<FracturedConstructEntity> FRACTURED_CONSTRUCT = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("fractured_construct"),
        FabricEntityTypeBuilder.<FracturedConstructEntity>create(MobCategory.MONSTER, FracturedConstructEntity::new)
            .dimensions(EntityDimensions.scalable(0.9f, 0.7f))
            .trackRangeBlocks(48)
            .trackedUpdateRate(3)
            .build()
    );

    public static final EntityType<RunestoneTitanEntity> RUNESTONE_TITAN = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("runestone_titan"),
        FabricEntityTypeBuilder.<RunestoneTitanEntity>create(MobCategory.MONSTER, RunestoneTitanEntity::new)
            .dimensions(EntityDimensions.scalable(2.5f, 5.0f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(3)
            .build()
    );

    private HexwrightMobEntities() {
    }

    public static void register() {
    }

    public static void registerAttributes() {
        FabricDefaultAttributeRegistry.register(EXPERIMENTAL_CONSTRUCT, ExperimentalConstructEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SERVITOR_CONSTRUCT, ServitorConstructEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FRACTURED_CONSTRUCT, FracturedConstructEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RUNESTONE_TITAN, RunestoneTitanEntity.createAttributes());
    }
}
