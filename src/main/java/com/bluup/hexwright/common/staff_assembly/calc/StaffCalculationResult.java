package com.bluup.hexwright.common.staff_assembly.calc;

import java.util.List;
import java.util.Optional;

public record StaffCalculationResult(
    Optional<CoreData> core,
    ComponentResult wrap,
    ComponentResult focus,
    ComponentResult catalyst,
    double coreAttunement,
    double totalAttunement,
    double overallEfficiency,
    EfficiencyRating efficiencyRating,
    double quality,
    EfficiencyRating qualityRating,
    boolean craftable,
    List<String> validationErrors
) {
}
