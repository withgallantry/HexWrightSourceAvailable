package com.bluup.hexwright.server.pentabox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class PentaboxEvents {
    private static final long SYNC_INTERVAL_TICKS = 10L;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.overworld().getGameTime() % SYNC_INTERVAL_TICKS != 0L) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncCarried(player);
            }
        });
    }

    private static void syncCarried(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (PentaboxData.isLinkedStack(stack)) {
                PentaboxData.syncDeployedStack(stack);
            }
        }
    }

    private PentaboxEvents() {
    }
}
