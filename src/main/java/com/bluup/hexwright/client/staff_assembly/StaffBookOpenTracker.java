package com.bluup.hexwright.client.staff_assembly;

import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import com.bluup.hexwright.client.hexicon.HexiconOverlay;
import com.bluup.hexwright.common.staff_assembly.StaffPart;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.hexicon.HexiconUIFactory;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffBookOpenTracker {
    private static final Map<UUID, Boolean> REMOTE_OPEN = new ConcurrentHashMap<>();
    private static Boolean lastSent;

    private StaffBookOpenTracker() {
    }

    public static void onClientTick(Minecraft client) {
        Player player = client.player;
        if (player == null) {
            lastSent = null;
            return;
        }

        ItemStack stack = heldBookStaff(player);
        if (stack.isEmpty()) {
            if (Boolean.TRUE.equals(lastSent)) {
                HexwrightNetworking.sendHexiconBookOpen(false);
            }
            lastSent = null;
            return;
        }

        boolean open = isOpenLocally(stack);
        if (lastSent == null || lastSent != open) {
            HexwrightNetworking.sendHexiconBookOpen(open);
            lastSent = open;
        }
    }

    public static boolean isOpenLocally(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof GuiSpellcasting) {
            return true;
        }

        if (!StaffPowers.hasHexiconCore(stack)) {
            return false;
        }

        if (HexiconOverlay.isActive()) {
            return true;
        }

        return client.screen instanceof ModularUIGuiContainer modularScreen
            && modularScreen.modularUI.holder instanceof HexiconUIFactory.Holder;
    }

    public static boolean isOpenRemote(UUID playerId) {
        return Boolean.TRUE.equals(REMOTE_OPEN.get(playerId));
    }

    public static void setRemoteOpen(UUID playerId, boolean open) {
        if (open) {
            REMOTE_OPEN.put(playerId, true);
        } else {
            REMOTE_OPEN.remove(playerId);
        }
    }

    private static ItemStack heldBookStaff(Player player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (isBookStaff(main)) {
            return main;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (isBookStaff(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isBookStaff(ItemStack stack) {
        return stack.is(HexwrightItems.CONFIGURABLE_STAFF) && StaffParts.isBookModel(stack);
    }
}
