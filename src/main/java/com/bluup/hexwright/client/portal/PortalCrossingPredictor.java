package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.portal.PortalManager;
import com.bluup.hexwright.server.portal.PortalPair;
import com.bluup.hexwright.server.portal.PortalTransform;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PortalCrossingPredictor {

    private static final int COOLDOWN_TICKS = 3;

    private static final double MAX_CROSSING_STEP = 4.0;

    private static final double PREDICT_MARGIN = 0.1;

    private static final double MAX_CROSSING_DEPTH = 2.5;

    private static final double ANCHOR_MIN_DEPTH = 0.02;

    private static @Nullable Vec3 prevCenter;
    private static int cooldown;

    private PortalCrossingPredictor() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PortalCrossingPredictor::onClientTick);
    }

    private static void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            prevCenter = null;
            cooldown = 0;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        Vec3 now = center(player);
        Vec3 prev = prevCenter == null ? now : prevCenter;
        prevCenter = now;
        if (cooldown > 0 || player.isSpectator() || player.isPassenger() || player.isRemoved()) {
            return;
        }
        if (prev.distanceToSqr(now) > MAX_CROSSING_STEP * MAX_CROSSING_STEP) {
            return;
        }

        for (ClientPortalManager.Entry entry : ClientPortalManager.entries()) {
            if (entry.closing() || ClientPortalManager.openProgress(entry, 1.0f) < 1.0f) {
                continue;
            }
            if (entry.pair().isCrossDimensional()) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                if (!ClientPortalManager.sideIsLocal(entry.pair(), side)) {
                    continue;
                }
                PortalWindow window = entry.pair().window(side);
                if (crosses(window, prev, now)) {
                    fold(player, entry.pair(), side, window.signedDistance(prev));
                    cooldown = COOLDOWN_TICKS;
                    prevCenter = center(player);
                    return;
                }
            }
        }
    }

    private static boolean crosses(PortalWindow window, Vec3 from, Vec3 to) {
        double sFrom = window.signedDistance(from);
        double sTo = window.signedDistance(to);
        if ((sFrom > 0.0) == (sTo > 0.0) || Math.abs(sFrom) > MAX_CROSSING_DEPTH) {
            return false;
        }
        double t = sFrom / (sFrom - sTo);
        Vec3 pierce = from.add(to.subtract(from).scale(t));
        return window.containsProjected(pierce, PREDICT_MARGIN);
    }

    private static void fold(LocalPlayer player, PortalPair pair, int side, double entrySign) {
        PortalTransform transform = pair.transformFrom(side);
        PortalWindow source = pair.window(side);
        PortalWindow destination = pair.window(1 - side);
        float yawDelta = transform.yawDeltaDegrees();
        Vec3 oldPos = new Vec3(player.xo, player.yo, player.zo);
        Vec3 velocity = player.getDeltaMovement();
        Vec3 newPos = PortalManager.exitPosition(transform, source, destination, player.position(), entrySign);

        Vec3 anchor = holdInFront(destination, transform.apply(oldPos), newPos);

        player.setPos(newPos);
        player.xo = anchor.x;
        player.yo = anchor.y;
        player.zo = anchor.z;
        player.xOld = anchor.x;
        player.yOld = anchor.y;
        player.zOld = anchor.z;

        player.setYRot(Mth.wrapDegrees(player.getYRot() + yawDelta));
        player.yRotO = Mth.wrapDegrees(player.yRotO + yawDelta);
        player.yBodyRot = Mth.wrapDegrees(player.yBodyRot + yawDelta);
        player.yBodyRotO = Mth.wrapDegrees(player.yBodyRotO + yawDelta);
        player.yHeadRot = Mth.wrapDegrees(player.yHeadRot + yawDelta);
        player.yHeadRotO = Mth.wrapDegrees(player.yHeadRotO + yawDelta);
        player.setDeltaMovement(transform.applyDirection(velocity));

        PortalViewRenderer.markFreshCrossing(newPos);
        HexwrightNetworking.sendPortalCross(pair.id(), side);
    }

    private static Vec3 holdInFront(PortalWindow destination, Vec3 anchor, Vec3 exit) {
        double exitSide = Math.signum(destination.signedDistance(exit));
        if (exitSide == 0.0) {
            return exit;
        }
        double depth = destination.signedDistance(anchor) * exitSide;
        if (depth >= ANCHOR_MIN_DEPTH) {
            return anchor;
        }
        return anchor.add(destination.normal().scale(exitSide * (ANCHOR_MIN_DEPTH - depth)));
    }

    private static Vec3 center(LocalPlayer player) {
        return player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
    }
}
