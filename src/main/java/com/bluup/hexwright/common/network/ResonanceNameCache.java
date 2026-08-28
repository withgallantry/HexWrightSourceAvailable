package com.bluup.hexwright.common.network;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ResonanceNameCache {

    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();

    private ResonanceNameCache() {
    }

    public static void apply(boolean full, Map<String, String> entries) {
        if (full) {
            NAMES.clear();
        }
        NAMES.putAll(entries);
    }

    public static void clear() {
        NAMES.clear();
    }

    public static @Nullable String nameOf(@Nullable String networkKey) {
        return networkKey == null ? null : NAMES.get(networkKey);
    }
}
