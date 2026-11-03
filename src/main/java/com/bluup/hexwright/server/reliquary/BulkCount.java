package com.bluup.hexwright.server.reliquary;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class BulkCount {

    public static final int THRESHOLD = 1_000;

    private BulkCount() {
    }

    public static boolean isBulk(long count) {
        return count >= THRESHOLD;
    }

    public static String format(long count) {
        if (count < 1_000L) {
            return Long.toString(count);
        }
        if (count < 1_000_000L) {
            return shorten(count, 1_000L, "k");
        }
        if (count < 1_000_000_000L) {
            return shorten(count, 1_000_000L, "m");
        }
        return shorten(count, 1_000_000_000L, "b");
    }

    public static Component exactLine(long count) {
        return Component.translatable("hexwright.reliquary.bulk_count", String.format(Locale.ROOT, "%,d", count))
            .withStyle(ChatFormatting.GRAY);
    }

    private static String shorten(long count, long unit, String suffix) {
        long tenths = count * 10 / unit;
        long whole = tenths / 10;
        long tenth = tenths % 10;
        if (whole >= 100 || tenth == 0) {
            return whole + suffix;
        }
        return whole + "." + tenth + suffix;
    }
}
