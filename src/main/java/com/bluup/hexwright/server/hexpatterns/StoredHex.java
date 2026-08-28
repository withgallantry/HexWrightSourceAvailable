package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class StoredHex {

    private StoredHex() {
    }

    public static @Nullable List<Iota> decode(@Nullable Iota iota) {
        if (iota instanceof PatternIota pattern) {
            return List.of(pattern);
        }
        if (iota instanceof ListIota list) {
            List<Iota> out = new ArrayList<>();
            for (Iota entry : list.getList()) {
                out.add(entry);
            }
            return out;
        }
        return null;
    }

    public static boolean isHex(@Nullable Iota iota) {
        return iota instanceof PatternIota || iota instanceof ListIota;
    }
}
