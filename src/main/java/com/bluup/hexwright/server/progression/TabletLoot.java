package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.item.StoneTabletItem;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public final class TabletLoot {

    private TabletLoot() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!source.isBuiltin()) {
                return;
            }
            for (RecipeTablets.LootDrop drop : RecipeTablets.get().drops()) {
                if (!drop.table().equals(id)) {
                    continue;
                }
                LootPool.Builder pool = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(drop.rolls()))
                    .when(LootItemRandomChanceCondition.randomChance(drop.chance()));
                for (RecipeTablets.LootEntry entry : drop.entries()) {
                    pool.add(
                        LootItem.lootTableItem(HexwrightItems.STONE_TABLET)
                            .setWeight(entry.weight())
                            .apply(SetNbtFunction.setTag(StoneTabletItem.lootTag(entry.recipe())))
                    );
                }
                tableBuilder.withPool(pool);
            }
        });
    }
}
