package com.bluup.hexwright.server.pentabox;

import com.bluup.hexwright.server.accessory.WornAccessories;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PentaboxEvents {
    private static final long SYNC_INTERVAL_TICKS = 10L;

    private static final long ORPHAN_GRACE_TICKS = 200L;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.overworld().getGameTime() % SYNC_INTERVAL_TICKS != 0L) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncCarried(server, player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            PentaboxStore.get(server).refreshOwner(
                handler.player.getUUID().toString(), server.overworld().getGameTime()));
    }

    private static void syncCarried(MinecraftServer server, ServerPlayer player) {
        PentaboxStore store = PentaboxStore.get(server);
        long now = server.overworld().getGameTime();

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!PentaboxData.isLinkedStack(stack)) {
                continue;
            }
            if (PentaboxData.discardStalePointer(server, stack)) {
                continue;
            }
            PentaboxData.normalizeProjection(server, player, stack);
            PentaboxData.syncDeployedStack(server, stack);
            store.touch(PentaboxData.getLinkedBoxUuid(stack), player.getUUID().toString(), now);
        }
        touchCarried(server, store, player, now);
        discardStaleInOpenContainer(server, player);

        recoverWornProjections(server, player);
        reclaimOrphans(server, player, store, now);
    }

    private static void touchCarried(MinecraftServer server, PentaboxStore store, ServerPlayer player, long now) {
        if (player.containerMenu == null) {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (!PentaboxData.isLinkedStack(carried) || PentaboxData.discardStalePointer(server, carried)) {
            return;
        }
        store.touch(PentaboxData.getLinkedBoxUuid(carried), player.getUUID().toString(), now);
    }

    private static void discardStaleInOpenContainer(MinecraftServer server, ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return;
        }
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (!PentaboxData.isLinkedStack(stack)) {
                continue;
            }
            if (PentaboxData.discardStalePointer(server, stack)) {
                slot.setChanged();
            }
        }
    }

    private static void reclaimOrphans(MinecraftServer server, ServerPlayer player, PentaboxStore store, long now) {
        List<String> stale = store.staleKeys(player.getUUID().toString(), now, ORPHAN_GRACE_TICKS);
        for (String key : stale) {
            ItemStack box = PentaboxData.reclaimOrphan(server, key);
            if (box.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(box)) {
                player.drop(box, false);
            }
        }
    }

    public static void onThrown(Player player, ItemStack thrown) {
        MinecraftServer server = player.getServer();
        if (server == null || !PentaboxData.isLinkedStack(thrown)) {
            return;
        }
        ItemStack box = PentaboxData.detachProjection(server, thrown);
        if (box.isEmpty()) {
            return;
        }
        if (player.isDeadOrDying()) {
            player.drop(box, false);
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getItem(inventory.selected).isEmpty()) {
            inventory.setItem(inventory.selected, box);
            return;
        }
        if (!inventory.add(box)) {
            player.drop(box, false);
        }
    }

    public static void onDied(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!PentaboxData.isLinkedStack(stack)) {
                continue;
            }
            PentaboxData.syncDeployedStack(server, stack);
            ItemStack box = PentaboxData.detachProjection(server, stack);
            if (box.isEmpty()) {
                continue;
            }
            if (!inventory.add(box)) {
                player.drop(box, false);
            }
        }
    }

    private static void recoverWornProjections(MinecraftServer server, ServerPlayer player) {
        for (ItemStack worn : WornAccessories.allWorn(player)) {
            if (!PentaboxData.isLinkedStack(worn)) {
                continue;
            }
            ItemStack box = PentaboxData.detachProjection(server, worn);
            if (box.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(box)) {
                player.drop(box, false);
            }
        }
    }

    private PentaboxEvents() {
    }
}
