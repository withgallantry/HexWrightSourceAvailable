package com.bluup.hexwright.client.vehicle;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.client.accessory.WornSpectacles;
import com.bluup.hexwright.server.vehicle.VehicleDebugSnapshot;
import com.bluup.hexwright.server.vehicle.VehicleEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VehicleDebugOverlay {

    private static final int POLL_INTERVAL_TICKS = 2;

    private static final int SNAPSHOT_LIFETIME_TICKS = 40;

    private static final int PANEL_WIDTH = 172;
    private static final int SCREEN_MARGIN = 6;
    private static final int PAD = 5;
    private static final int ROW = 10;
    private static final int DIVIDER_GAP = 3;
    private static final int SECTION_GAP = 4;

    private static final int CONTEXT_ROWS = 11;
    private static final int TAIL_ROWS = 3;

    private static final int COLOR_PANEL = 0xD00E1016;
    private static final int COLOR_BORDER = 0x60A8B4CC;
    private static final int COLOR_DIVIDER = 0x40A8B4CC;
    private static final int COLOR_TITLE = 0xFFF2EFE6;
    private static final int COLOR_LABEL = 0xFF8895AA;
    private static final int COLOR_VALUE = 0xFFC8D7FF;
    private static final int COLOR_HEADING = 0xFFD9C48C;

    private static int snapshotEntityId = -1;
    private static @Nullable VehicleDebugSnapshot snapshot;
    private static int snapshotAgeTicks;
    private static int pollCountdown;

    private VehicleDebugOverlay() {
    }

    public static void acceptSnapshot(int entityId, VehicleDebugSnapshot fresh) {
        snapshotEntityId = entityId;
        snapshot = fresh;
        snapshotAgeTicks = 0;
    }

    public static void onClientTick(Minecraft client) {
        VehicleEntity target = debugTarget(client);
        if (target == null) {
            snapshot = null;
            snapshotEntityId = -1;
            pollCountdown = 0;
            return;
        }
        if (snapshot != null && ++snapshotAgeTicks > SNAPSHOT_LIFETIME_TICKS) {
            snapshot = null;
        }
        if (--pollCountdown > 0) {
            return;
        }
        pollCountdown = POLL_INTERVAL_TICKS;
        HexwrightNetworking.requestVehicleDebug(target.getId());
    }

    public static boolean isShowing() {
        return debugTarget(Minecraft.getInstance()) != null;
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        VehicleEntity target = debugTarget(client);
        if (target == null) {
            return;
        }
        render(graphics, client, target, snapshotEntityId == target.getId() ? snapshot : null);
    }

    private static @Nullable VehicleEntity debugTarget(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null || client.options.hideGui) {
            return null;
        }
        if (!WornSpectacles.worn()) {
            return null;
        }
        if (client.player.getVehicle() instanceof VehicleEntity ridden) {
            return ridden;
        }
        return client.crosshairPickEntity instanceof VehicleEntity looked ? looked : null;
    }

    private static void render(
        GuiGraphics graphics, Minecraft client, VehicleEntity vehicle, @Nullable VehicleDebugSnapshot state
    ) {
        Font font = client.font;
        List<FormattedText> memoryLines = state == null
            ? List.of()
            : font.getSplitter().splitLines(
                Component.literal(state.memory()), PANEL_WIDTH, net.minecraft.network.chat.Style.EMPTY);

        int body = state == null
            ? ROW
            : CONTEXT_ROWS * ROW
                + SECTION_GAP + ROW + memoryLines.size() * ROW
                + SECTION_GAP + DIVIDER_GAP + TAIL_ROWS * ROW;
        int height = PAD * 2 + ROW + DIVIDER_GAP + body;

        int left = client.getWindow().getGuiScaledWidth() - SCREEN_MARGIN - PANEL_WIDTH - PAD;
        int top = (client.getWindow().getGuiScaledHeight() - height) / 2;

        graphics.fill(left, top, left + PANEL_WIDTH + PAD * 2, top + height, COLOR_PANEL);
        graphics.renderOutline(left, top, PANEL_WIDTH + PAD * 2, height, COLOR_BORDER);

        int x = left + PAD;
        int right = x + PANEL_WIDTH;
        int y = top + PAD;

        Component title = Component.translatable("hud.hexwright.vehicle.debug.title",
            Component.translatable(vehicle.variantNameKey()));
        graphics.drawString(font, title, x + (PANEL_WIDTH - font.width(title)) / 2, y, COLOR_TITLE);
        y += ROW;
        graphics.fill(x, y, right, y + 1, COLOR_DIVIDER);
        y += DIVIDER_GAP;

        if (state == null) {
            graphics.drawString(font, Component.translatable("hud.hexwright.vehicle.debug.waiting")
                .withStyle(ChatFormatting.ITALIC), x, y, COLOR_LABEL);
            return;
        }

        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.input", vec(state.riderInput()));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.position", vec(state.position()));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.velocity", vec(state.velocity()));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.speed",
            Component.literal(String.format("%.3f", state.velocity().length())));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.ground_speed",
            Component.literal(String.format("%.3f", Math.sqrt(
                state.velocity().x * state.velocity().x + state.velocity().z * state.velocity().z))));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.climb_speed",
            Component.literal(String.format("%+.3f", state.velocity().y)));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.forward", vec(state.riderForward()));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.right", vec(state.riderRight()));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.command", vec(state.previousCommand()));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.max_horizontal",
            Component.literal(String.format("%.3f", state.maxHorizontalSpeed())));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.max_vertical",
            Component.literal(String.format("%.3f", state.maxVerticalSpeed())));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.max_acceleration",
            Component.literal(String.format("%.3f", state.maxAcceleration())));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.media",
            Component.translatable("hud.hexwright.vehicle.reservoir_value",
                dust(state.internalMedia()), dust(state.mediaCapacity())));

        y += SECTION_GAP;
        graphics.drawString(font, Component.translatable("hud.hexwright.vehicle.debug.memory", state.memorySize()),
            x, y, COLOR_HEADING);
        y += ROW;
        for (FormattedText line : memoryLines) {
            graphics.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(line),
                x, y, COLOR_VALUE);
            y += ROW;
        }

        y += SECTION_GAP;
        graphics.fill(x, y, right, y + 1, COLOR_DIVIDER);
        y += DIVIDER_GAP;
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.cost",
            Component.literal(dust(state.lastMediaCost())));
        y = row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.next_run",
            Component.translatable("hud.hexwright.vehicle.debug.ticks", state.ticksUntilNextEvaluation()));
        row(graphics, font, x, right, y, "hud.hexwright.vehicle.debug.state", runState(state));
    }

    private static Component runState(VehicleDebugSnapshot state) {
        if (!state.hasHex()) {
            return Component.translatable("hud.hexwright.vehicle.debug.state.no_hex")
                .withStyle(ChatFormatting.GOLD);
        }
        return state.stalled()
            ? Component.translatable("hud.hexwright.vehicle.debug.state.stalled").withStyle(ChatFormatting.RED)
            : Component.translatable("hud.hexwright.vehicle.debug.state.flying").withStyle(ChatFormatting.GREEN);
    }

    private static int row(
        GuiGraphics graphics, Font font, int left, int right, int y, String labelKey, Component value
    ) {
        graphics.drawString(font, Component.translatable(labelKey), left, y, COLOR_LABEL);
        graphics.drawString(font, value, right - font.width(value), y, COLOR_VALUE);
        return y + ROW;
    }

    private static Component vec(Vec3 vec) {
        return Component.literal(String.format("%.2f %.2f %.2f", vec.x, vec.y, vec.z));
    }

    private static String dust(long media) {
        return String.format("%.1f", media / (double) MediaConstants.DUST_UNIT);
    }
}
