package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.google.gson.JsonObject;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class HexwrightCriteria {

    public static final QualityCraftedTrigger QUALITY_CRAFTED = new QualityCraftedTrigger();
    public static final EssenceCollectedTrigger ESSENCE_COLLECTED = new EssenceCollectedTrigger();

    private HexwrightCriteria() {
    }

    public static void register() {
        CriteriaTriggers.register(QUALITY_CRAFTED);
        CriteriaTriggers.register(ESSENCE_COLLECTED);
    }

    public static final class QualityCraftedTrigger extends SimpleCriterionTrigger<QualityCraftedTrigger.TriggerInstance> {
        static final ResourceLocation ID = Hexwright.id("quality_crafted");

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate predicate,
                                                 DeserializationContext context) {
            String qualityName = GsonHelper.getAsString(json, "min_quality", "crude");
            PocketCasterData.Quality minQuality =
                PocketCasterData.Quality.valueOf(qualityName.toUpperCase(Locale.ROOT));
            String item = GsonHelper.getAsString(json, "item", "");
            return new TriggerInstance(predicate, minQuality, item.isEmpty() ? null : item);
        }

        public void trigger(ServerPlayer player, PocketCasterData.Quality quality, ItemStack result) {
            String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            this.trigger(player, instance -> instance.matches(quality, itemId));
        }

        public static final class TriggerInstance extends AbstractCriterionTriggerInstance {
            private final PocketCasterData.Quality minQuality;
            private final @Nullable String item;

            TriggerInstance(ContextAwarePredicate predicate, PocketCasterData.Quality minQuality,
                            @Nullable String item) {
                super(ID, predicate);
                this.minQuality = minQuality;
                this.item = item;
            }

            boolean matches(PocketCasterData.Quality quality, String itemId) {
                if (quality.ordinal() < minQuality.ordinal()) {
                    return false;
                }
                return item == null || item.equals(itemId);
            }
        }
    }

    public static final class EssenceCollectedTrigger extends SimpleCriterionTrigger<EssenceCollectedTrigger.TriggerInstance> {
        static final ResourceLocation ID = Hexwright.id("essence_collected");

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate predicate,
                                                 DeserializationContext context) {
            double minTotal = GsonHelper.getAsDouble(json, "min_total", 0.0);
            return new TriggerInstance(predicate, minTotal);
        }

        public void trigger(ServerPlayer player, double total) {
            this.trigger(player, instance -> total >= instance.minTotal && total > 0);
        }

        public static final class TriggerInstance extends AbstractCriterionTriggerInstance {
            private final double minTotal;

            TriggerInstance(ContextAwarePredicate predicate, double minTotal) {
                super(ID, predicate);
                this.minTotal = minTotal;
            }
        }
    }
}
