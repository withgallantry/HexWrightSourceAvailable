package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.mixin.CameraAccessor;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ArrivalTrace {

    private static final int TRACE_TICKS = 60;
    private static final int APPROACH_TICKS = 20;
    private static final double APPROACH_DISTANCE = 2.0;

    private static int ticksLeft;
    private static String label = "";
    private static double lastY;
    private static long armedAtNanos;
    private static int frame;
    private static int tick;

    private ArrivalTrace() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ArrivalTrace::tick);
    }

    public static void arm(String into) {
        label = into;
        ticksLeft = TRACE_TICKS;
        lastY = Double.NaN;
        armedAtNanos = System.nanoTime();
        frame = 0;
        tick = 0;
    }

    private static void tick(Minecraft mc) {
        if (!HexwrightDebug.on(HexwrightDebug.PORTAL)) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null && (ticksLeft <= 0 || label.startsWith("approach"))
            && Math.abs(nearestCrossDimPane(player.getEyePosition())) <= APPROACH_DISTANCE) {
            if (ticksLeft <= 0) {
                arm("approach " + mc.level.dimension().location());
            }
            ticksLeft = Math.max(ticksLeft, APPROACH_TICKS);
        }
        if (ticksLeft <= 0) {
            return;
        }
        ticksLeft--;
        int t = ++tick;
        if (player == null || mc.level == null) {
            Hexwright.LOGGER.info("[arrival {}] t={} no player/level", label, t);
            return;
        }
        double y = player.getY();
        double step = Double.isNaN(lastY) ? 0.0 : y - lastY;
        lastY = y;
        BlockPos below = BlockPos.containing(player.getX(), y - 0.1, player.getZ());
        Hexwright.LOGGER.info(
            "[arrival {}] t={} y={} step={} yo={} vy={} onGround={} pose={} eye={} camY={} chunk={} below={} screen={} pane={}",
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
            mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
            String.format("%+.3f", nearestCrossDimPane(player.getEyePosition())));
    }

    public static void frame(Camera camera, Entity entity, float partialTick) {
        if (!HexwrightDebug.on(HexwrightDebug.PORTAL)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (ticksLeft <= 0 || camera != mc.gameRenderer.getMainCamera()) {
            return;
        }
        CameraAccessor access = (CameraAccessor) camera;
        Vec3 pos = camera.getPosition();
        Hexwright.LOGGER.info(
            "[arrival-frame {}] f={} ms={} pt={} feetY={} eyeOld={} eye={} camY={} camXZ={},{} entity={} level={} screen={} pane={}",
            label, frame++,
            (System.nanoTime() - armedAtNanos) / 1_000_000L,
            String.format("%.3f", partialTick),
            String.format("%.4f", Mth.lerp(partialTick, entity.yo, entity.getY())),
            String.format("%.3f", access.hexwright$getOldEyeHeight()),
            String.format("%.3f", access.hexwright$getEyeHeight()),
            String.format("%.4f", pos.y),
            String.format("%.2f", pos.x), String.format("%.2f", pos.z),
            entity == mc.player ? "player" : entity.getClass().getSimpleName(),
            mc.level == null ? "none" : mc.level.dimension().location(),
            mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
            String.format("%+.3f", nearestCrossDimPane(pos)));
    }

    private static double nearestCrossDimPane(Vec3 pos) {
        double best = Double.POSITIVE_INFINITY;
        for (ClientPortalManager.Entry entry : ClientPortalManager.entries()) {
            if (!entry.pair().isCrossDimensional()) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                if (!ClientPortalManager.sideIsLocal(entry.pair(), side)) {
                    continue;
                }
                PortalWindow window = entry.pair().window(side);
                double signed = window.signedDistance(pos);
                if (window.containsProjected(pos, 1.0) && Math.abs(signed) < Math.abs(best)) {
                    best = signed;
                }
            }
        }
        return best;
    }
}
