package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkshopFlourish {

    static final LootItemConditionType TYPE = new LootItemConditionType(new ConditionSerializer());

    private WorkshopFlourish() {
    }

    static void register() {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Hexwright.id("workshop_flourish"), TYPE);
    }

    private record Condition(float chance, int within) implements LootItemCondition {

        @Override
        public LootItemConditionType getType() {
            return TYPE;
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return Set.of(LootContextParams.THIS_ENTITY, LootContextParams.ORIGIN);
        }

        @Override
        public boolean test(LootContext context) {
            Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
            if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer opener) || origin == null
                || !DungeonDimension.isDungeonLevel(context.getLevel())) {
                return false;
            }
            DungeonInstance instance = DungeonInstances.at(opener.server, origin);
            if (instance == null) {
                return false;
            }
            Map<UUID, Integer> flourishes = instance.flourishes();
            Integer chests = flourishes.get(opener.getUUID());
            if (chests == null) {
                chests = context.getRandom().nextFloat() < this.chance ? 1 + context.getRandom().nextInt(this.within) : 0;
                HexwrightDebug.log(HexwrightDebug.DUNGEON, "[workshop] {} drew for a flourish in instance {}: {}",
                    opener.getGameProfile().getName(), instance.id(), chests > 0 ? "in chest " + chests : "none");
            }
            flourishes.put(opener.getUUID(), Math.max(chests - 1, 0));
            DungeonInstanceRegistry.get(opener.server).setDirty();
            return chests == 1;
        }
    }

    private static final class ConditionSerializer implements Serializer<Condition> {
        @Override
        public void serialize(JsonObject json, Condition condition, JsonSerializationContext context) {
            json.addProperty("chance", condition.chance());
            json.addProperty("within", condition.within());
        }

        @Override
        public Condition deserialize(JsonObject json, JsonDeserializationContext context) {
            return new Condition(GsonHelper.getAsFloat(json, "chance"), Math.max(1, GsonHelper.getAsInt(json, "within")));
        }
    }
}
