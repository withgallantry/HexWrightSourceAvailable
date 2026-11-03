package com.bluup.hexwright.client.vehicle;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.vehicle.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class VehicleDescendInputTracker {
    private static Boolean lastSent;

    private VehicleDescendInputTracker() {
    }

    public static void onClientTick(Minecraft client) {
        Player player = client.player;
        if (player == null || !(player.getVehicle() instanceof VehicleEntity)) {
            if (Boolean.TRUE.equals(lastSent)) {
                HexwrightNetworking.sendVehicleDescendInput(false);
            }
            lastSent = null;
            return;
        }

        boolean held = client.options.keySprint.isDown();
        if (lastSent == null || lastSent != held) {
            HexwrightNetworking.sendVehicleDescendInput(held);
            lastSent = held;
        }
    }
}
