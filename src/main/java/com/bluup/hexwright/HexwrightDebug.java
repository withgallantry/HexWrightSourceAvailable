package com.bluup.hexwright;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class HexwrightDebug {

    public static final String PORTAL = "portal";
    public static final String VAULT = "vault";
    public static final String CASTING = "casting";
    public static final String CONTENT = "content";
    public static final String RENDER = "render";
    public static final String WEAPON = "weapon";

    public static final Set<String> AREAS =
        Set.of(PORTAL, VAULT, CASTING, CONTENT, RENDER, WEAPON);

    private static final String PROPERTY = "hexwright.debug";

    private static final Set<String> FROM_LAUNCH = launchAreas();

    private static final Set<String> AT_RUNTIME = new CopyOnWriteArraySet<>();

    private static final Set<String> SILENCED = new CopyOnWriteArraySet<>();

    private static final boolean ALL_FROM_LAUNCH = FROM_LAUNCH.contains("*");

    private HexwrightDebug() {
    }

    public static boolean on() {
        if (ALL_FROM_LAUNCH || AT_RUNTIME.contains("*")) {
            return !SILENCED.containsAll(AREAS);
        }
        for (String area : AREAS) {
            if (on(area)) {
                return true;
            }
        }
        return false;
    }

    public static boolean on(String area) {
        if (SILENCED.contains(area)) {
            return false;
        }
        return ALL_FROM_LAUNCH
            || AT_RUNTIME.contains("*")
            || FROM_LAUNCH.contains(area)
            || AT_RUNTIME.contains(area);
    }

    public static void log(String area, String format, Object... args) {
        if (on(area)) {
            Hexwright.LOGGER.info(format, args);
        }
    }

    public static void enable(String area) {
        SILENCED.remove(area);
        AT_RUNTIME.add(area);
    }

    public static void disable(String area) {
        AT_RUNTIME.remove(area);
        SILENCED.add(area);
    }

    public static Set<String> active() {
        Set<String> live = new LinkedHashSet<>();
        for (String area : AREAS) {
            if (on(area)) {
                live.add(area);
            }
        }
        return live;
    }

    private static Set<String> launchAreas() {
        Set<String> named = new LinkedHashSet<>();

        for (String area : AREAS) {
            if ("true".equals(System.getProperty("hexwright." + area + ".debug"))) {
                named.add(area);
            }
        }

        String raw = System.getProperty(PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("HEXWRIGHT_DEBUG");
        }
        if (raw == null || raw.isBlank()) {
            return Set.copyOf(named);
        }
        raw = raw.trim().toLowerCase(Locale.ROOT);
        if (raw.equals("false") || raw.equals("off") || raw.equals("none") || raw.equals("0")) {
            return Set.copyOf(named);
        }
        if (raw.equals("true") || raw.equals("all") || raw.equals("*") || raw.equals("1")) {
            return Set.of("*");
        }
        for (String part : raw.split(",")) {
            String area = part.trim();
            if (!area.isEmpty()) {
                named.add(area);
            }
        }
        String[] unknown = named.stream().filter(area -> !AREAS.contains(area)).toArray(String[]::new);
        if (unknown.length > 0) {
            Hexwright.LOGGER.warn("-D{} names no such debug area: {}. Known areas: {}",
                PROPERTY, Arrays.toString(unknown), AREAS);
        }
        return Set.copyOf(named);
    }
}
