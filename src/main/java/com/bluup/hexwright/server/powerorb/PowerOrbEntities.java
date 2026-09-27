package com.bluup.hexwright.server.powerorb;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class PowerOrbEntities {

    public static final EntityType<SpiritGolemEntity> SPIRIT_GOLEM = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("spirit_golem"),
        FabricEntityTypeBuilder.<SpiritGolemEntity>create(MobCategory.MISC, SpiritGolemEntity::new)
            .dimensions(EntityDimensions.fixed(1.4f, 2.7f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(2)
            .fireImmune()
            .build()
    );

    public static final EntityType<CatConstructEntity> CAT_CONSTRUCT = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("cat_construct"),
        FabricEntityTypeBuilder.<CatConstructEntity>create(MobCategory.MISC, CatConstructEntity::new)
            .dimensions(EntityDimensions.fixed(0.875f, 1.7f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(2)
            .fireImmune()
            .build()
    );

    public static final EntityType<GuardianConstructEntity> GUARDIAN_CONSTRUCT = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("guardian_construct"),
        FabricEntityTypeBuilder.<GuardianConstructEntity>create(MobCategory.MISC, GuardianConstructEntity::new)
            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(1)
            .fireImmune()
            .build()
    );

    public static final EntityType<SpiritWardEntity> SPIRIT_WARD = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("spirit_ward"),
        FabricEntityTypeBuilder.<SpiritWardEntity>create(MobCategory.MISC, SpiritWardEntity::new)
            .dimensions(EntityDimensions.fixed((float) (SpiritWardEntity.RADIUS * 2.0D), 2.5f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(20)
            .fireImmune()
            .build()
    );

    public static final EntityType<SanctuaryHandsEntity> SANCTUARY_HANDS = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("sanctuary_hands"),
        FabricEntityTypeBuilder.<SanctuaryHandsEntity>create(MobCategory.MISC, SanctuaryHandsEntity::new)
            .dimensions(EntityDimensions.fixed((float) (SanctuaryHandsEntity.RADIUS * 2.0D), 4.0f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(20)
            .fireImmune()
            .build()
    );

    private PowerOrbEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(SPIRIT_GOLEM, SpiritGolemEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CAT_CONSTRUCT, CatConstructEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GUARDIAN_CONSTRUCT, GuardianConstructEntity.createAttributes());
    }
}
