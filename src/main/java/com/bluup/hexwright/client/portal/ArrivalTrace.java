package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class ArrivalTrace {

    private static final int TRACE_TICKS = 60;

    private static int ticksLeft;
    private static String label = "";
    private static double lastY;

    private ArrivalTrace() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ArrivalTrace::tick);
    }

    public static void arm(String into) {
        label = into;
        ticksLeft = TRACE_TICKS;
        lastY = Double.NaN;
    }

    private static void tick(Minecraft mc) {
        if (ticksLeft <= 0) {
            return;
        }
        int t = TRACE_TICKS - --ticksLeft;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            Hexwright.LOGGER.info("[arrival {}] t={} no player/level", label, t);
            return;
        }
        double y = player.getY();
        double step = Double.isNaN(lastY) ? 0.0 : y - lastY;
        lastY = y;
        BlockPos below = BlockPos.containing(player.getX(), y - 0.1, player.getZ());
        Hexwright.LOGGER.info(
            "[arrival {}] t={} y={} step={} yo={} vy={} onGround={} pose={} eye={} camY={} chunk={} below={} screen={}",
            label, t,
            String.format("%.4f", y),
            String.format("%+.4f", step),
            String.format("%.4f", player.yo),
            String.format("%+.4f", player.getDeltaMovement().y),
            player.onGround(),
            player.getPose(),
            String.format("%.3f", player.getEyeHeight()),
            String.format("%.4f", mc.gameRenderer.getMainCamera().getPosition().y),
            mc.level.hasChunkAt(player.getBlockX(), player.getBlockZ()),
            mc.level.getBlockState(below).getBlock(),
            mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
    }
}
