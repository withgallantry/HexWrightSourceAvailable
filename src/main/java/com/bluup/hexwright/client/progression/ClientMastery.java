package com.bluup.hexwright.client.progression;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import org.jetbrains.annotations.Nullable;

public final class ClientMastery {

    private static volatile int bestQuality = -1;

    private static volatile int version;

    private ClientMastery() {
    }

    public static void setBestQuality(int ordinal) {
        bestQuality = ordinal;
        version++;
    }

    public static @Nullable PocketCasterData.Quality bestQuality() {
        return bestQuality < 0 ? null : PocketCasterData.Quality.values()[bestQuality];
    }

    public static boolean hasMastery(PocketCasterData.Quality required) {
        return bestQuality >= required.ordinal();
    }

    public static int version() {
        return version;
    }
}
