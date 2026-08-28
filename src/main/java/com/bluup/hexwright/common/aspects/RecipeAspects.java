package com.bluup.hexwright.common.aspects;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.aspects.AspectMappings.AspectProfile;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientData;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientRegistry;
import com.bluup.hexwright.server.coalescence.CoalescenceRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecipeAspects {

    public static final double EFFICIENCY = 0.85;

    private static final double ATTUNEMENT_RATIO = 0.6;

    private static final int MAX_CATEGORIES = 4;

    private static final int MAX_PASSES = 10;

    private static final double MIN_VALUE = 0.1;

    private RecipeAspects() {
    }

    public static void rebuild(MinecraftServer server) {
        rebuild(server.getRecipeManager(), server.registryAccess());
    }

    public static void rebuild(RecipeManager recipeManager, RegistryAccess access) {
        long startNanos = System.nanoTime();

        AspectMappings.setDerived(Map.of());

        List<Recipe<?>> recipes = new ArrayList<>();
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (isDerivableType(recipe.getType())) {
                recipes.add(recipe);
            }
        }
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));

        Map<Item, AspectProfile> derived = new HashMap<>();
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            for (Recipe<?> recipe : recipes) {
                changed |= deriveFrom(recipe, access, derived);
            }
            if (!changed) {
                break;
            }
        }

        AspectMappings.setDerived(derived);
        CoalescenceRecipes.invalidate();
        Hexwright.LOGGER.info("Derived aspect profiles for {} item(s) from {} recipe(s) in {} ms",
            derived.size(), recipes.size(), (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static boolean isDerivableType(RecipeType<?> type) {
        return type == RecipeType.CRAFTING
            || type == RecipeType.SMELTING
            || type == RecipeType.BLASTING
            || type == RecipeType.SMOKING
            || type == RecipeType.CAMPFIRE_COOKING
            || type == RecipeType.STONECUTTING;
    }

    private static boolean deriveFrom(Recipe<?> recipe, RegistryAccess access, Map<Item, AspectProfile> derived) {
        ItemStack result;
        try {
            result = recipe.getResultItem(access);
        } catch (Exception e) {
            return false;
        }
        if (result == null || result.isEmpty()) {
            return false;
        }
        Item item = result.getItem();
        if (isHandAuthored(item)) {
            return false;
        }

        double inTotal = 0;
        Map<IngredientCategory, Double> weights = new EnumMap<>(IngredientCategory.class);
        int usedIngredients = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            Option cheapest = cheapestOption(ingredient, derived);
            if (cheapest == null) {
                continue;
            }
            double contribution = Math.max(0, essenceTotal(cheapest.data()) - remainderCredit(cheapest.item()));
            inTotal += contribution;
            for (IngredientCategory category : cheapest.data().categories()) {
                weights.merge(category, contribution, Double::sum);
            }
            usedIngredients++;
        }
        if (usedIngredients == 0 || inTotal <= 0 || weights.isEmpty()) {
            return false;
        }

        Set<IngredientCategory> categories = topCategories(weights);
        int count = Math.max(1, result.getCount());
        double outTotal = EFFICIENCY * inTotal / count;
        double value = outTotal / categories.size();
        if (value < MIN_VALUE) {
            return false;
        }

        AspectProfile existing = derived.get(item);
        if (existing != null && essenceTotal(existing.data()) <= outTotal + 1.0E-9) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        IngredientData data = new IngredientData(
            id.getPath(),
            Component.literal(prettify(id.getPath())),
            value,
            categories,
            value * ATTUNEMENT_RATIO
        );
        derived.put(item, new AspectProfile(data, AspectMappings.defaultBurnTicks(value), value));
        return true;
    }

    private static boolean isHandAuthored(Item item) {
        return AspectMappings.lookupData(item) != null || IngredientRegistry.hasBuiltinOverride(item);
    }

    private record Option(Item item, IngredientData data) {
    }

    private static @Nullable Option cheapestOption(Ingredient ingredient, Map<Item, AspectProfile> derived) {
        Option cheapest = null;
        String cheapestId = "";
        for (ItemStack option : ingredient.getItems()) {
            if (option.isEmpty()) {
                continue;
            }
            IngredientData data = resolve(option.getItem(), derived);
            if (data == null) {
                continue;
            }
            String optionId = BuiltInRegistries.ITEM.getKey(option.getItem()).toString();
            if (cheapest == null
                || essenceTotal(data) < essenceTotal(cheapest.data()) - 1.0E-9
                || (Math.abs(essenceTotal(data) - essenceTotal(cheapest.data())) <= 1.0E-9 && optionId.compareTo(cheapestId) < 0)) {
                cheapest = new Option(option.getItem(), data);
                cheapestId = optionId;
            }
        }
        return cheapest;
    }

    private static @Nullable IngredientData resolve(Item item, Map<Item, AspectProfile> derived) {
        AspectProfile derivedProfile = derived.get(item);
        if (derivedProfile != null) {
            return derivedProfile.data();
        }
        return IngredientRegistry.lookup(item).orElse(null);
    }

    private static double essenceTotal(IngredientData data) {
        return data.baseValue() * Math.max(1, data.categories().size());
    }

    private static double remainderCredit(Item item) {
        Item remainder = item.getCraftingRemainingItem();
        if (remainder == null) {
            return 0;
        }
        IngredientData remainderData = IngredientRegistry.lookup(remainder).orElse(null);
        return remainderData == null ? 0 : essenceTotal(remainderData);
    }

    private static Set<IngredientCategory> topCategories(Map<IngredientCategory, Double> weights) {
        Set<IngredientCategory> categories = EnumSet.noneOf(IngredientCategory.class);
        weights.entrySet().stream()
            .sorted(Map.Entry.<IngredientCategory, Double>comparingByValue().reversed()
                .thenComparing(entry -> entry.getKey().name()))
            .limit(MAX_CATEGORIES)
            .forEach(entry -> categories.add(entry.getKey()));
        return categories;
    }

    private static String prettify(String path) {
        StringBuilder name = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}
