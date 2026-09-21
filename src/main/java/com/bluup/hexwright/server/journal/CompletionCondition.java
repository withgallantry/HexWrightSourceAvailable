package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.server.worldgen.decadentvault.DecadentVaultRegistry;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonRooms;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public interface CompletionCondition {

    boolean isMet(ServerPlayer player, InvestigationState state);

    default void collectCounted(List<Counted> into) {
    }

    default boolean discoversStructure() {
        return false;
    }

    interface Counted extends CompletionCondition {

        String counterKey();

        int required();
    }


    static CompletionCondition parse(JsonObject json) {
        String type = GsonHelper.getAsString(json, "type");
        return switch (type) {
            case "obtain_item" -> new ObtainItem(
                Matcher.parse(GsonHelper.getAsString(json, "item")),
                GsonHelper.getAsInt(json, "count", 1));
            case "kill_entity" -> new Kill(
                Matcher.parse(GsonHelper.getAsString(json, "entity")),
                GsonHelper.getAsInt(json, "count", 1));
            case "crucible_burn" -> new CrucibleBurn(
                parseAspect(GsonHelper.getAsString(json, "aspect", null)),
                GsonHelper.getAsDouble(json, "amount", 1.0));
            case "visit_biome" -> new VisitBiome(Matcher.parse(GsonHelper.getAsString(json, "biome")));
            case "visit_structure" -> new VisitStructure(Matcher.parse(GsonHelper.getAsString(json, "structure")));
            case "visit_dungeon_room" -> new VisitDungeonRoom(
                new ResourceLocation(GsonHelper.getAsString(json, "room")));
            case "visit_decadent_vault" -> VisitDecadentVault.INSTANCE;
            case "visit_dimension" -> new VisitDimension(
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(GsonHelper.getAsString(json, "dimension"))));
            case "advancement" -> new HasAdvancement(new ResourceLocation(GsonHelper.getAsString(json, "advancement")));
            case "all_of" -> new AllOf(parseList(json));
            case "any_of" -> new AnyOf(parseList(json));
            case "manual" -> Manual.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown investigation completion type '" + type + "'");
        };
    }

    private static @Nullable IngredientCategory parseAspect(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return IngredientCategory.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown essence aspect '" + raw + "' - expected one of "
                + Arrays.toString(IngredientCategory.values()).toLowerCase(Locale.ROOT));
        }
    }

    private static List<CompletionCondition> parseList(JsonObject json) {
        JsonArray array = GsonHelper.getAsJsonArray(json, "conditions");
        List<CompletionCondition> parsed = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            parsed.add(parse(GsonHelper.convertToJsonObject(array.get(i), "conditions[" + i + "]")));
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("all_of/any_of needs at least one condition");
        }
        return List.copyOf(parsed);
    }

    record Matcher(ResourceLocation id, boolean tag) {

        static Matcher parse(String raw) {
            return raw.startsWith("#")
                ? new Matcher(new ResourceLocation(raw.substring(1)), true)
                : new Matcher(new ResourceLocation(raw), false);
        }

        @Override
        public String toString() {
            return (tag ? "#" : "") + id;
        }

        boolean matches(ItemStack stack) {
            return tag
                ? stack.is(TagKey.create(Registries.ITEM, id))
                : stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
        }

        boolean matches(EntityType<?> type) {
            return tag ? type.is(TagKey.create(Registries.ENTITY_TYPE, id))
                : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(id);
        }

        boolean matchesBiome(Holder<Biome> biome) {
            return tag
                ? biome.is(TagKey.create(Registries.BIOME, id))
                : biome.is(ResourceKey.create(Registries.BIOME, id));
        }
    }


    record ObtainItem(Matcher item, int count) implements CompletionCondition {
        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            int found = 0;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && item.matches(stack)) {
                    found += stack.getCount();
                    if (found >= count) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    record VisitBiome(Matcher biome) implements CompletionCondition {
        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            return biome.matchesBiome(player.level().getBiome(player.blockPosition()));
        }
    }

    record VisitStructure(Matcher structure) implements CompletionCondition {
        @Override
        public boolean discoversStructure() {
            return true;
        }

        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            if (!(player.level() instanceof ServerLevel level)) {
                return false;
            }
            StructureStart start;
            if (structure.tag()) {
                start = level.structureManager().getStructureWithPieceAt(
                    player.blockPosition(), TagKey.create(Registries.STRUCTURE, structure.id()));
            } else {
                Structure resolved = level.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .get(structure.id());
                if (resolved == null) {
                    return false;
                }
                start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), resolved);
            }
            return start.isValid();
        }
    }

    record VisitDungeonRoom(ResourceLocation room) implements CompletionCondition {
        @Override
        public boolean discoversStructure() {
            return true;
        }

        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            return player.level() instanceof ServerLevel level
                && DungeonRooms.isInside(level, player.blockPosition(), room);
        }
    }

    record VisitDecadentVault() implements CompletionCondition {
        static final VisitDecadentVault INSTANCE = new VisitDecadentVault();

        @Override
        public boolean discoversStructure() {
            return true;
        }

        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            return DecadentVaultRegistry.get(player.server)
                .isInside(player.level().dimension(), player.position());
        }
    }

    record VisitDimension(ResourceKey<Level> dimension) implements CompletionCondition {
        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            return player.level().dimension().equals(dimension);
        }
    }

    record HasAdvancement(ResourceLocation advancement) implements CompletionCondition {
        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            @Nullable Advancement found = player.server.getAdvancements().getAdvancement(advancement);
            return found != null && player.getAdvancements().getOrStartProgress(found).isDone();
        }
    }


    record Kill(Matcher entity, int count) implements Counted {
        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            return state.counter(player.getUUID(), counterKey()) >= count;
        }

        @Override
        public void collectCounted(List<Counted> into) {
            into.add(this);
        }

        @Override
        public String counterKey() {
            return "kill:" + entity;
        }

        @Override
        public int required() {
            return count;
        }

        boolean watches(Entity dead) {
            return entity.matches(dead.getType());
        }
    }

    record CrucibleBurn(@Nullable IngredientCategory aspect, double amount) implements Counted {

        static final int SCALE = 100;

        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            return state.counter(player.getUUID(), counterKey()) >= required();
        }

        @Override
        public void collectCounted(List<Counted> into) {
            into.add(this);
        }

        @Override
        public String counterKey() {
            return "crucible:" + (aspect == null ? "*" : aspect.name().toLowerCase(Locale.ROOT));
        }

        @Override
        public int required() {
            return (int) Math.min(Integer.MAX_VALUE, Math.round(amount * SCALE));
        }

        boolean watches(IngredientCategory produced) {
            return aspect == null || aspect == produced;
        }
    }


    record AllOf(List<CompletionCondition> conditions) implements CompletionCondition {
        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            for (CompletionCondition condition : conditions) {
                if (!condition.isMet(player, state)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void collectCounted(List<Counted> into) {
            conditions.forEach(condition -> condition.collectCounted(into));
        }

        @Override
        public boolean discoversStructure() {
            for (CompletionCondition condition : conditions) {
                if (condition.discoversStructure()) {
                    return true;
                }
            }
            return false;
        }
    }

    record AnyOf(List<CompletionCondition> conditions) implements CompletionCondition {
        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            for (CompletionCondition condition : conditions) {
                if (condition.isMet(player, state)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void collectCounted(List<Counted> into) {
            conditions.forEach(condition -> condition.collectCounted(into));
        }

        @Override
        public boolean discoversStructure() {
            for (CompletionCondition condition : conditions) {
                if (condition.discoversStructure()) {
                    return true;
                }
            }
            return false;
        }
    }

    final class Manual implements CompletionCondition {
        static final Manual INSTANCE = new Manual();

        private Manual() {
        }

        @Override
        public boolean isMet(ServerPlayer player, InvestigationState state) {
            return false;
        }
    }
}
