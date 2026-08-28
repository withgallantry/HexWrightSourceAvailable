package com.bluup.hexwright.server.reliquary;

import java.util.Locale;

public final class BulkCount {

    private BulkCount() {
    }

    public static String format(long count) {
        if (count < 1_000L) {
            return Long.toString(count);
        }
        if (count < 1_000_000L) {
            return shorten(count / 1_000.0, "k");
        }
        if (count < 1_000_000_000L) {
            return shorten(count / 1_000_000.0, "m");
        }
        return shorten(count / 1_000_000_000.0, "b");
    }

    private static String shorten(double value, String suffix) {
        if (value < 10.0) {
            return String.format(Locale.ROOT, "%.1f%s", Math.floor(value * 10.0) / 10.0, suffix);
        }
        return String.format(Locale.ROOT, "%d%s", (long) value, suffix);
    }
}
