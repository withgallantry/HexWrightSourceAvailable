package com.bluup.hexwright.server.soulforge;

import com.bluup.hexwright.Hexwright;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Set;
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
        Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Hexwright.id("release_soulforged"), Release.TYPE);
    }

    public static final class Release extends LootItemConditionalFunction {

        static final LootItemFunctionType TYPE = new LootItemFunctionType(new Serializer());

        private Release(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ItemStack run(ItemStack stack, LootContext context) {
            if (stack.getItem() instanceof SoulforgedItem
                && context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer opener) {
                SoulBond.issue(stack, opener.getUUID(), opener.getGameProfile().getName());
            }
            return stack;
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return Set.of(LootContextParams.THIS_ENTITY);
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
