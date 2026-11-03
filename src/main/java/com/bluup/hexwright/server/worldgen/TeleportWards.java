package com.bluup.hexwright.server.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TeleportWards {

    public interface Check {
        boolean isEmpty(ServerLevel level);

        boolean refuses(ServerLevel level, Vec3 from, Vec3 to);

        void notifyRefused(ServerPlayer player);
    }

    private static final List<Check> CHECKS = new ArrayList<>();

    private TeleportWards() {
    }

    public static void register(Check check) {
        CHECKS.add(check);
    }

    @Nullable
    public static Check firstRefusing(ServerLevel level, Vec3 from, Vec3 to) {
        for (Check check : CHECKS) {
            if (!check.isEmpty(level) && check.refuses(level, from, to)) {
                return check;
            }
        }
        return null;
    }
}
