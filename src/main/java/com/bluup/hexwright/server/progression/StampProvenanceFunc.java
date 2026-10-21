package com.bluup.hexwright.server.progression;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.Nullable;

public class StampProvenanceFunc extends LootItemConditionalFunction {

    public static final LootItemFunctionType TYPE = new LootItemFunctionType(new Serializer());

    public StampProvenanceFunc(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static LootItemConditionalFunction.Builder<?> stamp() {
        return simpleBuilder(StampProvenanceFunc::new);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        return ArtifactProvenance.stamp(stack, context.getLevel().getServer(), finder(context));
    }

    private static @Nullable ServerPlayer finder(LootContext context) {
        if (context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer opener) {
            return opener;
        }
        if (context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER) instanceof ServerPlayer killer) {
            return killer;
        }
        return context.getParamOrNull(LootContextParams.KILLER_ENTITY) instanceof ServerPlayer killer ? killer : null;
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<StampProvenanceFunc> {
        @Override
        public StampProvenanceFunc deserialize(JsonObject object, JsonDeserializationContext context,
                                               LootItemCondition[] conditions) {
            return new StampProvenanceFunc(conditions);
        }
    }
}
