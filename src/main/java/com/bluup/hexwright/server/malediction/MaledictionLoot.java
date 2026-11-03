package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.boss.UnlistedTableLoot;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Map;
import java.util.Set;

public final class MaledictionLoot {

    private static final ResourceLocation UNCOMMON = Hexwright.id("chests/maledictions/uncommon");
    private static final ResourceLocation RARE = Hexwright.id("chests/maledictions/rare");

    private static final float ORDINARY = 0.05F;
    private static final float DEEP_UNCOMMON = 0.08F;
    private static final float DEEP_RARE = 0.04F;

    private static final Map<ResourceLocation, Float> ORDINARY_TABLES = Map.of(
        new ResourceLocation("chests/simple_dungeon"), ORDINARY,
        new ResourceLocation("chests/abandoned_mineshaft"), ORDINARY,
        new ResourceLocation("chests/desert_pyramid"), ORDINARY,
        new ResourceLocation("chests/jungle_temple"), ORDINARY,
        new ResourceLocation("chests/pillager_outpost"), ORDINARY,
        new ResourceLocation("chests/shipwreck_treasure"), ORDINARY,
        new ResourceLocation("chests/underwater_ruin_big"), ORDINARY,
        new ResourceLocation("chests/stronghold_corridor"), ORDINARY);

    private static final Set<ResourceLocation> DEEP_TABLES = Set.of(
        new ResourceLocation("chests/stronghold_library"),
        new ResourceLocation("chests/ancient_city"),
        new ResourceLocation("chests/woodland_mansion"),
        new ResourceLocation("chests/bastion_treasure"));

    private MaledictionLoot() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!source.isBuiltin()) {
                return;
            }
            Float ordinary = ORDINARY_TABLES.get(id);
            if (ordinary != null) {
                addPool(tableBuilder, UNCOMMON, ordinary);
            } else if (DEEP_TABLES.contains(id)) {
                addPool(tableBuilder, UNCOMMON, DEEP_UNCOMMON);
                addPool(tableBuilder, RARE, DEEP_RARE);
            }
        });
    }

    private static void addPool(LootTable.Builder table, ResourceLocation pieces, float chance) {
        table.withPool(LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1))
            .when(LootItemRandomChanceCondition.randomChance(chance))
            .add(UnlistedTableLoot.table(pieces)));
    }
}
