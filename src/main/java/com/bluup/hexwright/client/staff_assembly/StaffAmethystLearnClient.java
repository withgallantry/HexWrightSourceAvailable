package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.staff_assembly.StaffGreatSpellData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

public final class StaffAmethystLearnClient {
    private static boolean attackWasDown;

    private StaffAmethystLearnClient() {
    }

    public static void onClientTick(Minecraft client) {
        boolean attackDown = client.options.keyAttack.isDown();

        if (!attackDown) {
            attackWasDown = false;
            return;
        }

        if (attackWasDown) {
            return;
        }
        attackWasDown = true;

        if (client.screen != null) {
            return;
        }

        Player player = client.player;
        if (player == null) {
            return;
        }

        if (client.hitResult != null && client.hitResult.getType() != HitResult.Type.MISS) {
            return;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (!main.is(HexwrightItems.CONFIGURABLE_STAFF)) {
            return;
        }
        if (!StaffGreatSpellData.isGreatSpellScroll(off)) {
            return;
        }

        HexwrightNetworking.sendAmethystLearn(InteractionHand.MAIN_HAND);
    }
}
