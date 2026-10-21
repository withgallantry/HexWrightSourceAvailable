package com.bluup.hexwright.server.soulforge;

import com.bluup.hexwright.Hexwright;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.function.Consumer;

public final class SoulforgeLoot {

    private SoulforgeLoot() {
    }

    public static Consumer<ItemStack> withoutUndroppable(Consumer<ItemStack> output) {
        return stack -> {
            if (Soulforge.drops(stack.getItem())) {
                output.accept(stack);
            }
        };
    }

    static void register() {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Hexwright.id("soulforged_in_pool"), InPool.TYPE);
        Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Hexwright.id("release_soulforged"), Release.TYPE);
    }

    public record InPool(Item item) implements LootItemCondition {

        static final LootItemConditionType TYPE = new LootItemConditionType(new InPoolSerializer());

        @Override
        public LootItemConditionType getType() {
            return TYPE;
        }

        @Override
        public boolean test(LootContext context) {
            return Soulforge.inPool(context.getLevel().getServer(), this.item);
        }
    }

    private static final class InPoolSerializer implements Serializer<InPool> {
        @Override
        public void serialize(JsonObject json, InPool condition, JsonSerializationContext context) {
            json.addProperty("item", BuiltInRegistries.ITEM.getKey(condition.item()).toString());
        }

        @Override
        public InPool deserialize(JsonObject json, JsonDeserializationContext context) {
            return new InPool(BuiltInRegistries.ITEM.get(new ResourceLocation(GsonHelper.getAsString(json, "item"))));
        }
    }

    public static final class Release extends LootItemConditionalFunction {

        static final LootItemFunctionType TYPE = new LootItemFunctionType(new Serializer());

        private Release(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ItemStack run(ItemStack stack, LootContext context) {
            if (!(stack.getItem() instanceof SoulforgedItem)) {
                return stack;
            }
            return Soulforge.releaseFromPool(context.getLevel().getServer(), stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public LootItemFunctionType getType() {
            return TYPE;
        }

        private static final class Serializer extends LootItemConditionalFunction.Serializer<Release> {
            @Override
            public Release deserialize(JsonObject object, JsonDeserializationContext context,
                                       LootItemCondition[] conditions) {
                return new Release(conditions);
            }
        }
    }
}
