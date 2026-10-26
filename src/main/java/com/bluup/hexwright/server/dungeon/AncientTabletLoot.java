package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.server.progression.RecipeUnlocks;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class AncientTabletLoot extends LootPoolSingletonContainer {

    static final LootPoolEntryType TYPE = new LootPoolEntryType(new Serializer());

    private AncientTabletLoot(int weight, int quality, LootItemCondition[] conditions, LootItemFunction[] functions) {
        super(weight, quality, conditions, functions);
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        ServerPlayer opener = context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player
            ? player
            : null;
        List<AncientPortalVariant> unlearned = Arrays.stream(AncientPortalVariant.values())
            .filter(variant -> opener == null || !RecipeUnlocks.isUnlocked(opener, variant.recipeKey()))
            .toList();
        if (!unlearned.isEmpty()) {
            output.accept(AncientTabletItem.issue(unlearned.get(context.getRandom().nextInt(unlearned.size())), opener));
        }
    }

    @Override
    public LootPoolEntryType getType() {
        return TYPE;
    }

    private static final class Serializer extends LootPoolSingletonContainer.Serializer<AncientTabletLoot> {
        @Override
        protected AncientTabletLoot deserialize(JsonObject object, JsonDeserializationContext context, int weight,
                                                int quality, LootItemCondition[] conditions,
                                                LootItemFunction[] functions) {
            return new AncientTabletLoot(weight, quality, conditions, functions);
        }
    }
}
