package com.bluup.hexwright;

import net.fabricmc.loader.api.FabricLoader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HexwrightDev {

    private static final String PROPERTY = "hexwright.dev";

    private static final String ARTISANS_SELECTION_NAME = "artisans_selection";

    private static final String LOOM_TOOLS_NAME = "loom_tools";

    private static final Set<String> FEATURES = Set.of(ARTISANS_SELECTION_NAME, LOOM_TOOLS_NAME);

    private static final Set<String> ENABLED = launchFeatures();

    public static final boolean ARTISANS_SELECTION = ENABLED.contains(ARTISANS_SELECTION_NAME);

    public static final boolean LOOM_TOOLS = ENABLED.contains(LOOM_TOOLS_NAME);

    private HexwrightDev() {
    }

    private static Set<String> launchFeatures() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("HEXWRIGHT_DEV");
        }
        if (raw == null || raw.isBlank()) {
            return FabricLoader.getInstance().isDevelopmentEnvironment() ? FEATURES : Set.of();
        }
        raw = raw.trim().toLowerCase(Locale.ROOT);
        if (raw.equals("false") || raw.equals("off") || raw.equals("none") || raw.equals("0")) {
            return Set.of();
        }
        if (raw.equals("true") || raw.equals("all") || raw.equals("*") || raw.equals("1")) {
            return FEATURES;
        }
        Set<String> named = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String feature = part.trim();
            if (!feature.isEmpty()) {
                named.add(feature);
            }
        }
        List<String> unknown = named.stream().filter(feature -> !FEATURES.contains(feature)).toList();
        if (!unknown.isEmpty()) {
            Hexwright.LOGGER.warn("-D{} names no such dev feature: {}. Known features: {}",
                PROPERTY, unknown, FEATURES);
        }
        return Set.copyOf(named);
    }
}
