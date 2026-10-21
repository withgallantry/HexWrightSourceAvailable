package com.bluup.hexwright.server.inspiration;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public final class InspirationLevels {

    public static final int POINTS_PER_LEVEL = 10;

    public static final int MAX_COMMIT = 3;

    public static final int TICKS_PER_LEVEL = 45 * 20;

    private InspirationLevels() {
    }

    public static int levels(int total) {
        return total / POINTS_PER_LEVEL;
    }

    public static int into(int total) {
        return total % POINTS_PER_LEVEL;
    }

    public static float fraction(int total) {
        return into(total) / (float) POINTS_PER_LEVEL;
    }

    public static int cost(int levels) {
        return levels * POINTS_PER_LEVEL;
    }

    public static int clockTicks(int levels) {
        return levels * TICKS_PER_LEVEL;
    }

    public static boolean canAfford(int total, int levels) {
        return levels >= 1 && total >= cost(levels);
    }

    public static boolean spend(ServerPlayer player, int levels) {
        InspirationState state = InspirationState.get(player.server);
        int total = state.total(player.getUUID());
        if (!canAfford(total, levels)) {
            return false;
        }
        int remaining = state.add(player.getUUID(), -cost(levels));
        HexwrightNetworking.sendInspirationSync(player);
        HexwrightDebug.log(HexwrightDebug.INSPIRATION, "[inspiration] {} staked {} level(s): {} left",
            player.getGameProfile().getName(), levels, remaining);
        return true;
    }

    public static int affordable(int total) {
        return Mth.clamp(levels(total), 0, MAX_COMMIT);
    }
}
