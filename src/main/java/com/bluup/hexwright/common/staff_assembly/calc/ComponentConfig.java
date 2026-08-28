package com.bluup.hexwright.common.staff_assembly.calc;

import java.util.Map;

public record ComponentConfig(double maxOutput, double usableValueCap, String statLabel, Map<IngredientCategory, Double> categoryModifiers) {
}
