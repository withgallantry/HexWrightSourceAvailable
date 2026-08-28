package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.portal.PortalInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PortalAttackHandler {

    private static @Nullable UUID pairId;
    private static int side;

    private PortalAttackHandler() {
    }

    public static boolean startAttack(Minecraft mc) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null || mc.missTime > 0
            || player.isHandsBusy() || player.isSpectator()) {
            return false;
        }
        ClientPortalManager.PortalPick pick =
            ClientPortalManager.pickPortal(player, gameMode.getPickRange(), mc.hitResult);
        if (pick == null) {
            release();
            return false;
        }
        gameMode.stopDestroyBlock();
        send(pick, PortalInteraction.ATTACK_START);
        player.swing(InteractionHand.MAIN_HAND);
        player.resetAttackStrengthTicker();
        return true;
    }

    public static boolean continueAttack(Minecraft mc, boolean holding) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (!holding || player == null || gameMode == null || mc.missTime > 0
            || player.isHandsBusy() || player.isSpectator()) {
            release();
            return false;
        }
        ClientPortalManager.PortalPick pick =
            ClientPortalManager.pickPortal(player, gameMode.getPickRange(), mc.hitResult);
        if (pick == null) {
            release();
            return false;
        }
        gameMode.stopDestroyBlock();
        boolean samePane = pairId != null && pairId.equals(pick.pairId()) && side == pick.side();
        if (!samePane) {
            release();
            send(pick, PortalInteraction.ATTACK_START);
        } else {
            send(pick, PortalInteraction.ATTACK_CONTINUE);
        }
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static void release() {
        if (pairId == null) {
            return;
        }
        HexwrightNetworking.sendPortalAttack(pairId, side, PortalInteraction.ATTACK_STOP);
        pairId = null;
    }

    private static void send(ClientPortalManager.PortalPick pick, int action) {
        pairId = pick.pairId();
        side = pick.side();
        HexwrightNetworking.sendPortalAttack(pairId, side, action);
    }

    public static void reset() {
        pairId = null;
    }
}
