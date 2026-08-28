package com.bluup.hexwright.server.coalescence;

import com.bluup.hexwright.common.aspects.AspectMappings;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public record CoalescenceRecipe(
    ResourceLocation id,
    ItemStack result,
    Map<IngredientCategory, Double> cost
) {

    public static final double MIN_COST_RATIO = 1.25;

    public double totalCost() {
        double total = 0;
        for (double amount : cost.values()) {
            total += amount;
        }
        return total;
    }

    public static double smeltBackTotal(ItemStack stack) {
        return AspectMappings.profileFor(stack.getItem())
            .map(profile -> profile.essenceYield() * profile.data().categories().size())
            .orElse(0.0) * stack.getCount();
    }

    public static double minimumCost(ItemStack result) {
        return smeltBackTotal(result) * MIN_COST_RATIO;
    }

    public String validate() {
        if (result.isEmpty()) {
            return "result is empty";
        }
        if (cost.isEmpty() || totalCost() <= 0) {
            return "cost is empty";
        }
        double minimum = minimumCost(result);
        if (totalCost() < minimum) {
            return String.format(Locale.ROOT,
                "total cost %.1f is below the safe minimum %.1f (%.2fx the %.1f essence the result smelts back into)",
                totalCost(), minimum, MIN_COST_RATIO, smeltBackTotal(result));
        }
        return null;
    }

    public static CoalescenceRecipe fromJson(ResourceLocation id, JsonObject json) {
        JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
        ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(resultJson, "item"));
        Item item = BuiltInRegistries.ITEM.get(itemId);
        int count = GsonHelper.getAsInt(resultJson, "count", 1);
        ItemStack result = new ItemStack(item, Math.max(1, count));

        Map<IngredientCategory, Double> cost = new EnumMap<>(IngredientCategory.class);
        JsonObject essence = GsonHelper.getAsJsonObject(json, "essence");
        for (Map.Entry<String, JsonElement> entry : essence.entrySet()) {
            IngredientCategory category = IngredientCategory.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            double amount = entry.getValue().getAsDouble();
            if (amount > 0) {
                cost.merge(category, amount, Double::sum);
            }
        }
        return new CoalescenceRecipe(id, result, cost);
    }
}
