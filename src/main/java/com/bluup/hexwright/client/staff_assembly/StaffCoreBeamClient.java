package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.HexwrightItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

public final class StaffCoreBeamClient {
    private static boolean beamActiveLastTick;

    private StaffCoreBeamClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StaffCoreBeamClient::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        Player player = client.player;
        boolean beamActive = player != null
            && client.screen == null
            && client.options.keyAttack.isDown()
            && isConfigurableStaff(player.getMainHandItem());

        if (beamActive) {
            HexwrightNetworking.sendStaffCoreBeam(true, crosshairFree(client), leftClickIsBusy(client));
        } else if (beamActiveLastTick) {
            HexwrightNetworking.sendStaffCoreBeam(false, true, false);
        }

        beamActiveLastTick = beamActive;
    }

    private static boolean crosshairFree(Minecraft client) {
        return client.hitResult == null || client.hitResult.getType() == HitResult.Type.MISS;
    }

    private static boolean leftClickIsBusy(Minecraft client) {
        if (client.gameMode != null && client.gameMode.isDestroying()) {
            return true;
        }
        return client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY;
    }

    private static boolean isConfigurableStaff(ItemStack stack) {
        return !stack.isEmpty() && stack.is(HexwrightItems.CONFIGURABLE_STAFF);
    }
}
