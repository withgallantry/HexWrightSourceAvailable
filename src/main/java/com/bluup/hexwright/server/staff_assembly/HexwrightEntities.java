package com.bluup.hexwright.server.staff_assembly;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.vehicle.BroomEntity;
import com.bluup.hexwright.server.vehicle.CarpetEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class HexwrightEntities {
    public static final EntityType<StaffCoreBoltEntity> STAFF_CORE_BOLT = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("staff_core_bolt"),
        FabricEntityTypeBuilder.<StaffCoreBoltEntity>create(MobCategory.MISC, StaffCoreBoltEntity::new)
            .dimensions(EntityDimensions.scalable(0.25f, 0.25f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(10)
            .build()
    );

    public static final EntityType<com.bluup.hexwright.server.weapon.HexArrowEntity> HEX_ARROW = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("hex_arrow"),
        FabricEntityTypeBuilder.<com.bluup.hexwright.server.weapon.HexArrowEntity>create(
                MobCategory.MISC, com.bluup.hexwright.server.weapon.HexArrowEntity::new)
            .dimensions(EntityDimensions.scalable(0.5f, 0.5f))
            .trackRangeChunks(4)
            .trackedUpdateRate(20)
            .build()
    );

    public static final EntityType<com.bluup.hexwright.server.weapon.SlashWaveEntity> SLASH_WAVE =
        Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Hexwright.id("slash_wave"),
            FabricEntityTypeBuilder.<com.bluup.hexwright.server.weapon.SlashWaveEntity>create(
                    MobCategory.MISC, com.bluup.hexwright.server.weapon.SlashWaveEntity::new)
                .dimensions(EntityDimensions.scalable(1.0f, 0.6f))
                .trackRangeBlocks(64)
                .trackedUpdateRate(1)
                .fireImmune()
                .build()
        );

    public static final EntityType<BroomEntity> BROOM = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("broom"),
        FabricEntityTypeBuilder.<BroomEntity>create(MobCategory.MISC, BroomEntity::new)
            .dimensions(EntityDimensions.scalable(1.4f, 0.4f))
            .trackRangeBlocks(80)
            .trackedUpdateRate(3)
            .build()
    );

    public static final EntityType<CarpetEntity> CARPET = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        Hexwright.id("carpet"),
        FabricEntityTypeBuilder.<CarpetEntity>create(MobCategory.MISC, CarpetEntity::new)
            .dimensions(EntityDimensions.scalable(1.6f, 0.4f))
            .trackRangeBlocks(80)
            .trackedUpdateRate(3)
            .build()
    );

    private HexwrightEntities() {
    }

    public static void register() {
    }
}
