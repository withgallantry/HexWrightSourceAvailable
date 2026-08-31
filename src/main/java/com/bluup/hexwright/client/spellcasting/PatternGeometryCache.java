package com.bluup.hexwright.client.spellcasting;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.world.phys.Vec2;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PatternGeometryCache {
    private static final int MAX_ENTRIES = 1024;

    private static final Map<HexPattern, Entry> LINES = new IdentityHashMap<>();

    private PatternGeometryCache() {
    }

    public static List<Vec2> toLines(HexPattern pattern, float hexSize, Vec2 origin) {
        if (!PatternDrawBatch.isEnabled()) {
            return pattern.toLines(hexSize, origin);
        }

        Entry cached = LINES.get(pattern);
        if (cached != null && cached.matches(hexSize, origin)) {
            return cached.lines();
        }

        if (LINES.size() >= MAX_ENTRIES) {
            LINES.clear();
        }

        List<Vec2> lines = pattern.toLines(hexSize, origin);
        LINES.put(pattern, new Entry(hexSize, origin.x, origin.y, lines));
        return lines;
    }

    private record Entry(float hexSize, float originX, float originY, List<Vec2> lines) {
        boolean matches(float otherHexSize, Vec2 otherOrigin) {
            return hexSize == otherHexSize && originX == otherOrigin.x && originY == otherOrigin.y;
        }
    }
}
