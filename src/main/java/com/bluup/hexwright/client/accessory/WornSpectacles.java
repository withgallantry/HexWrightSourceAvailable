package com.bluup.hexwright.client.accessory;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.accessory.WornAccessories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class WornSpectacles {

    private static long cachedAtGameTime = Long.MIN_VALUE;
    private static boolean cachedAnswer;

    private WornSpectacles() {
    }

    public static boolean worn() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return false;
        }
        long now = mc.level.getGameTime();
        if (now != cachedAtGameTime) {
            cachedAtGameTime = now;
            cachedAnswer = WornAccessories.isWearing(player, HexwrightItems.WARDERS_SPECTACLES);
        }
        return cachedAnswer;
    }

    public static void invalidate() {
        cachedAtGameTime = Long.MIN_VALUE;
    }
}
