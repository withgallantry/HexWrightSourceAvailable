package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class StarterJournal {

    private StarterJournal() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> give(handler.player));
    }

    public static boolean give(ServerPlayer player) {
        if (!InvestigationState.get(player.server).claimStarterJournal(player.getUUID())) {
            return false;
        }
        ItemStack journal = new ItemStack(HexwrightItems.FIELD_JOURNAL);
        if (!player.getInventory().add(journal)) {
            player.drop(journal, false);
        }
        return true;
    }
}
