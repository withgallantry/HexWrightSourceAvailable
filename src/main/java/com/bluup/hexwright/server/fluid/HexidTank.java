package com.bluup.hexwright.server.fluid;

import at.petrak.hexcasting.api.misc.MediaConstants;
import net.minecraft.util.Mth;

public final class HexidTank {

    public static final int MAX_HEIGHT = 6;

    public static final long BUCKET_MB = 1000;

    public static final long PER_BLOCK_MB = 4 * BUCKET_MB;

    public static final int MAX_MEDIA_PER_MB = (int) MediaConstants.DUST_UNIT;

    public static final double DRAMS_PER_BLOCK = 2000.0;

    public static long capacityMb(int height) {
        return Math.max(0, height) * PER_BLOCK_MB;
    }

    public static double dramCapacity(int height) {
        return Math.max(0, height) * DRAMS_PER_BLOCK;
    }

    public static int clampMediaPerMb(long mediaPerMb) {
        return (int) Mth.clamp(mediaPerMb, 0L, (long) MAX_MEDIA_PER_MB);
    }

    public static long totalMedia(long amountMb, int mediaPerMb) {
        return Math.max(0, amountMb) * (long) mediaPerMb;
    }

    public static long mediaCeiling(long amountMb) {
        return totalMedia(amountMb, MAX_MEDIA_PER_MB);
    }

    public static double saturation(int mediaPerMb) {
        return (double) mediaPerMb / MAX_MEDIA_PER_MB;
    }

    private HexidTank() {
    }
}
