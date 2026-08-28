package com.bluup.hexwright.common.staff_assembly.calc;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StaffCalculator {
    public static final double MAX_ATTUNEMENT = 100.0;

    public static final int MAX_ITEMS_PER_COMPONENT = 6;

    public static final ComponentConfig WRAP_CONFIG     = new ComponentConfig(60.0, 60.0, "Grid Size", CategoryModifiers.WRAP);
    public static final ComponentConfig FOCUS_CONFIG    = new ComponentConfig(8.0, 90.0, "Ambit", CategoryModifiers.FOCUS);
    public static final ComponentConfig CATALYST_CONFIG = new ComponentConfig(30.0, 120.0, "Reserve", CategoryModifiers.CATALYST);

    public static final double BASELINE_VALUE_DENSITY = 1.5;

    private StaffCalculator() {
    }

    public static StaffCalculationResult calculate(ItemStack coreStack, List<ItemStack> wrapStacks, List<ItemStack> focusStacks, List<ItemStack> catalystStacks) {
        List<String> errors = new ArrayList<>();

        Optional<CoreData> core = Optional.empty();
        double coreAttunement = 0;
        if (!coreStack.isEmpty()) {
            core = CoreRegistry.lookup(coreStack.getItem());
            if (core.isPresent()) {
                coreAttunement = core.get().attunementCost();
            } else {
                errors.add("Unknown Core item: " + coreStack.getItem());
            }
        } else {
            errors.add("No Core selected.");
        }

        if (wrapStacks.size() > MAX_ITEMS_PER_COMPONENT) {
            errors.add("Wrap cannot contain more than " + MAX_ITEMS_PER_COMPONENT + " items.");
        }
        if (focusStacks.size() > MAX_ITEMS_PER_COMPONENT) {
            errors.add("Focus cannot contain more than " + MAX_ITEMS_PER_COMPONENT + " items.");
        }
        if (catalystStacks.size() > MAX_ITEMS_PER_COMPONENT) {
            errors.add("Catalyst cannot contain more than " + MAX_ITEMS_PER_COMPONENT + " items.");
        }

        ComponentResult wrap = computeComponent(wrapStacks, WRAP_CONFIG);
        ComponentResult focus = computeComponent(focusStacks, FOCUS_CONFIG);
        ComponentResult catalyst = computeComponent(catalystStacks, CATALYST_CONFIG);

        addOverBudgetErrors(errors, wrap, focus, catalyst);

        double totalAttunement = coreAttunement + wrap.attunementCost() + focus.attunementCost() + catalyst.attunementCost();

        double componentAttunement = wrap.attunementCost() + focus.attunementCost() + catalyst.attunementCost();
        double overallEfficiency = componentAttunement <= 0
            ? 0.0
            : (wrap.efficiency() * wrap.attunementCost() + focus.efficiency() * focus.attunementCost() + catalyst.efficiency() * catalyst.attunementCost()) / componentAttunement;

        EfficiencyRating efficiencyRating = EfficiencyRating.fromEfficiency(overallEfficiency);


        double attunementUsage = clamp01(totalAttunement / MAX_ATTUNEMENT);
        double quality = overallEfficiency * attunementUsage;
        EfficiencyRating qualityRating = EfficiencyRating.fromEfficiency(quality);

        boolean craftable = errors.isEmpty() && totalAttunement <= MAX_ATTUNEMENT;

        return new StaffCalculationResult(core, wrap, focus, catalyst, coreAttunement, totalAttunement, overallEfficiency, efficiencyRating, quality, qualityRating, craftable, errors);
    }

    private static ComponentResult computeComponent(List<ItemStack> stacks, ComponentConfig config) {
        double statValue = 0;
        double attunementCost = 0;
        double fitWeightedSum = 0;
        int itemCount = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            itemCount++;
            Optional<IngredientData> ingredient = IngredientRegistry.lookup(stack.getItem());
            if (ingredient.isPresent()) {
                IngredientData data = ingredient.get();
                double multiplier = CategoryModifiers.multiplierFor(data.categories(), config.categoryModifiers());
                double itemAttunement = data.attunementCost() * stack.getCount();
                statValue += data.baseValue() * multiplier * stack.getCount();
                attunementCost += itemAttunement;

                double valueDensity = data.attunementCost() <= 0 ? 0.0 : data.baseValue() / data.attunementCost();
                double densityFactor = valueDensity / BASELINE_VALUE_DENSITY;
                double itemFit = Math.min(multiplier * densityFactor, 1.0);
                fitWeightedSum += itemFit * itemAttunement;
            }
        }

        if (itemCount == 0) {
            return ComponentResult.EMPTY;
        }

        double usableValue = Math.min(statValue, config.usableValueCap());
        double outputProgress = usableValue / config.usableValueCap();
        double output = outputProgress * config.maxOutput();

        double fit = attunementCost <= 0 ? 0.0 : fitWeightedSum / attunementCost;
        double capUsage = statValue <= 0 ? 1.0 : usableValue / statValue;
        double efficiency = clamp01(fit * capUsage);

        return new ComponentResult(output, statValue, usableValue, attunementCost, efficiency, itemCount);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static final String OVER_BUDGET_SUFFIX = " is over budget.";

    private static void addOverBudgetErrors(List<String> errors, ComponentResult wrap, ComponentResult focus, ComponentResult catalyst) {
        addOverBudgetError(errors, "Wrap", wrap, WRAP_CONFIG);
        addOverBudgetError(errors, "Focus", focus, FOCUS_CONFIG);
        addOverBudgetError(errors, "Catalyst", catalyst, CATALYST_CONFIG);
    }

    private static void addOverBudgetError(List<String> errors, String label, ComponentResult component, ComponentConfig config) {
        if (config.usableValueCap() > 0 && component.statValue() > config.usableValueCap()) {
            errors.add(label + OVER_BUDGET_SUFFIX);
        }
    }

    private static boolean isOverBudgetError(String error) {
        return error.endsWith(OVER_BUDGET_SUFFIX);
    }

    public static StaffCalculationResult mergeWithPersisted(StaffCalculationResult live, PersistedStaffStats persisted) {
        ComponentResult wrap = live.wrap().itemCount() > 0 ? live.wrap() : persisted.wrap();
        ComponentResult focus = live.focus().itemCount() > 0 ? live.focus() : persisted.focus();
        ComponentResult catalyst = live.catalyst().itemCount() > 0 ? live.catalyst() : persisted.catalyst();
        double coreAttunement = live.core().isPresent() ? live.coreAttunement() : persisted.coreAttunement();

        double totalAttunement = coreAttunement + wrap.attunementCost() + focus.attunementCost() + catalyst.attunementCost();

        double componentAttunement = wrap.attunementCost() + focus.attunementCost() + catalyst.attunementCost();
        double overallEfficiency = componentAttunement <= 0
            ? 0.0
            : (wrap.efficiency() * wrap.attunementCost() + focus.efficiency() * focus.attunementCost() + catalyst.efficiency() * catalyst.attunementCost()) / componentAttunement;
        EfficiencyRating efficiencyRating = EfficiencyRating.fromEfficiency(overallEfficiency);

        double attunementUsage = clamp01(totalAttunement / MAX_ATTUNEMENT);
        double quality = overallEfficiency * attunementUsage;
        EfficiencyRating qualityRating = EfficiencyRating.fromEfficiency(quality);

        boolean hasCore = live.core().isPresent() || persisted.coreAttunement() > 0;
        List<String> errors = new ArrayList<>();
        for (String error : live.validationErrors()) {
            if (error.equals("No Core selected.") && hasCore) {
                continue;
            }
            if (isOverBudgetError(error)) {
                continue;
            }
            errors.add(error);
        }
        addOverBudgetErrors(errors, wrap, focus, catalyst);

        boolean craftable = errors.isEmpty() && totalAttunement <= MAX_ATTUNEMENT;

        return new StaffCalculationResult(live.core(), wrap, focus, catalyst, coreAttunement, totalAttunement, overallEfficiency, efficiencyRating, quality, qualityRating, craftable, errors);
    }

    public static boolean isCraftable(StaffCalculationResult merged) {
        return merged.craftable();
    }
}
