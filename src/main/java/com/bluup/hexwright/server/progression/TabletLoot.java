package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public final class TabletLoot {

    private TabletLoot() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!source.isBuiltin()) {
                return;
            }
            RecipeTablets.Config config = RecipeTablets.get();
            for (RecipeTablets.TabletDrop drop : config.ruinedDrops()) {
                if (drop.tables().contains(id)) {
                    addTablets(tableBuilder, drop, HexwrightItems.RUINED_TABLET, false);
                }
            }
            for (RecipeTablets.TabletDrop drop : config.artifactDrops()) {
                if (drop.tables().contains(id)) {
                    addTablets(tableBuilder, drop, HexwrightItems.ARTIFACT_RUINED_TABLET, true);
                }
            }
        });
    }

    private static void addTablets(LootTable.Builder table, RecipeTablets.TabletDrop drop, Item item, boolean stamp) {
        LootPoolSingletonContainer.Builder<?> tablet = LootItem.lootTableItem(item);
        if (drop.maxCount() > drop.minCount()) {
            tablet.apply(SetItemCountFunction.setCount(UniformGenerator.between(drop.minCount(), drop.maxCount())));
        } else if (drop.minCount() > 1) {
            tablet.apply(SetItemCountFunction.setCount(ConstantValue.exactly(drop.minCount())));
        }
        if (stamp) {
            tablet.apply(StampProvenanceFunc.stamp());
        }
        table.withPool(
            LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(drop.chance()))
                .add(tablet)
        );
    }
}
