package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class FillBottleFunc extends LootItemConditionalFunction {

    public static final LootItemFunctionType TYPE = new LootItemFunctionType(new Serializer());

    public FillBottleFunc(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.is(HexwrightItems.HEX_ENGRAVED_BOTTLE)) {
            return stack;
        }
        RemnantType[] types = RemnantType.values();
        BottleData.pour(stack, new Remnant(types[context.getRandom().nextInt(types.length)],
            BottleData.capacity(stack)));
        return stack;
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<FillBottleFunc> {
        @Override
        public FillBottleFunc deserialize(JsonObject object, JsonDeserializationContext context,
                                          LootItemCondition[] conditions) {
            return new FillBottleFunc(conditions);
        }
    }
}
