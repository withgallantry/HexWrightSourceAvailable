package com.bluup.hexwright.common.staff_assembly.calc;

import net.minecraft.network.chat.Component;

import java.util.Set;

public record IngredientData(String id, Component displayName, double baseValue, Set<IngredientCategory> categories, double attunementCost) {
}
