package com.bluup.hexwright.client.vehicle;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.client.tooltip.GradedTooltips;
import com.bluup.hexwright.client.tooltip.TooltipStyle;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.vehicle.VehicleConfig;
import com.bluup.hexwright.server.vehicle.VehicleEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class VehicleFlightHud {

    private static final int CONTENT_WIDTH = 132;
    private static final int SCREEN_MARGIN = 8;

    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_ZONE_HEIGHT = 40;

    private static final int TITLE_TO_BODY = 12;
    private static final int TEXT_ROW = 10;
    private static final int BAR_ROW = 8;
    private static final int BAR_HEIGHT = 5;

    private static final int COLOR_LABEL = 0xFFA9A5B8;
    private static final int COLOR_VALUE = 0xFFF2EFE6;
    private static final int COLOR_BAR_TRACK = 0x80121016;
    private static final int COLOR_BAR_EDGE = 0x40FFFFFF;
    private static final int COLOR_BAR_SPEED = 0xFF8FD3FF;
    private static final int COLOR_BAR_MEDIA = 0xFFB784E8;
    private static final int COLOR_BAR_MEDIA_LOW = 0xFFE0605A;
    private static final int COLOR_SPEED_OVERSPEED = 0xFFFF7A6E;
    private static final int COLOR_BAR_SPEED_OVERSPEED = 0xFFE0605A;

    private static final float WOBBLE_TICKS = 9.0f;
    private static final float WOBBLE_CYCLES = 3.0f;
    private static final float WOBBLE_AMPLITUDE = 2.5f;

    private static final float MEDIA_LOW_FRACTION = 0.15f;

    private static final int TICKS_PER_SECOND = 20;

    private static final float SPEED_SMOOTHING = 0.35f;

    private static float smoothedSpeed;
    private static int smoothedSpeedVehicleId = -1;
    private static boolean wasOverspeed;
    private static float wobbleAge = WOBBLE_TICKS;

    private VehicleFlightHud() {
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        VehicleEntity vehicle = ridingVehicle(client);
        if (vehicle == null) {
            smoothedSpeedVehicleId = -1;
            return;
        }
        render(graphics, client, vehicle);
    }

    private static @Nullable VehicleEntity ridingVehicle(Minecraft client) {
        if (client.player == null || client.screen != null || client.options.hideGui) {
            return null;
        }
        return client.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }

    private static void render(GuiGraphics graphics, Minecraft client, VehicleEntity vehicle) {
        Font font = client.font;
        PocketCasterData.Quality grade = vehicle.getQuality();

        float speed = displaySpeed(client, vehicle);
        long media = vehicle.getInternalMedia();
        Component status = statusLine(vehicle, media);

        boolean showReservoir = media > 0L;

        int contentHeight = TITLE_TO_BODY
            + TEXT_ROW + BAR_ROW
            + (showReservoir ? TEXT_ROW + BAR_ROW + TEXT_ROW : 0)
            + TEXT_ROW
            + (status == null ? 0 : TEXT_ROW);

        int contentLeft = client.getWindow().getGuiScaledWidth()
            - SCREEN_MARGIN - TooltipStyle.PAD_SIDE - CONTENT_WIDTH;
        int skinHeight = contentHeight + TooltipStyle.PAD_TOP + TooltipStyle.PAD_BOTTOM;
        int contentTop = panelBottom(client, contentLeft) - skinHeight + TooltipStyle.PAD_TOP;
        int contentRight = contentLeft + CONTENT_WIDTH;

        GradedTooltips.styleFor(grade).render(graphics, contentLeft, contentTop, CONTENT_WIDTH, contentHeight, 0);

        Component title = Component.translatable(vehicle.variantNameKey()).withStyle(grade.color());
        graphics.drawString(font, title, contentLeft + (CONTENT_WIDTH - font.width(title)) / 2, contentTop, COLOR_VALUE);

        int y = contentTop + TITLE_TO_BODY;

        float maxSpeed = (float) vehicle.getMaxHorizontalSpeed() * TICKS_PER_SECOND;
        boolean overspeed = vehicle.isOverspeed();
        int shake = speedRowShake(client, overspeed);
        drawStat(graphics, font, contentLeft + shake, contentRight + shake, y,
            Component.translatable("hud.hexwright.vehicle.speed"),
            Component.translatable("hud.hexwright.vehicle.speed_value", String.format("%.1f", speed)),
            overspeed ? COLOR_SPEED_OVERSPEED : COLOR_VALUE);
        y += TEXT_ROW;
        drawBar(graphics, contentLeft + shake, contentRight + shake, y, speed / maxSpeed,
            overspeed ? COLOR_BAR_SPEED_OVERSPEED : COLOR_BAR_SPEED);
        y += BAR_ROW;

        if (showReservoir) {
            long capacity = Math.max(1L, vehicle.getMediaCapacity());
            float mediaFraction = media / (float) capacity;
            drawStat(graphics, font, contentLeft, contentRight, y,
                Component.translatable("hud.hexwright.vehicle.reservoir"),
                Component.translatable("hud.hexwright.vehicle.reservoir_value",
                    String.format("%.1f", media / (double) MediaConstants.DUST_UNIT),
                    capacity / MediaConstants.DUST_UNIT));
            y += TEXT_ROW;
            drawBar(graphics, contentLeft, contentRight, y, mediaFraction,
                mediaFraction < MEDIA_LOW_FRACTION ? COLOR_BAR_MEDIA_LOW : COLOR_BAR_MEDIA);
            y += BAR_ROW;

            drawStat(graphics, font, contentLeft, contentRight, y,
                Component.translatable("hud.hexwright.vehicle.flight_time"),
                flightTime(vehicle, media));
            y += TEXT_ROW;
        }

        drawStat(graphics, font, contentLeft, contentRight, y,
            Component.translatable("hud.hexwright.vehicle.altitude"),
            Component.literal(String.valueOf(Mth.floor(vehicle.getY()))));
        y += TEXT_ROW;

        if (status != null) {
            graphics.drawString(font, status, contentLeft + (CONTENT_WIDTH - font.width(status)) / 2, y, COLOR_VALUE);
        }
    }

    private static int panelBottom(Minecraft client, int contentLeft) {
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int bottom = client.getWindow().getGuiScaledHeight() - SCREEN_MARGIN;
        if (contentLeft - TooltipStyle.PAD_SIDE < screenWidth / 2 + HOTBAR_HALF_WIDTH) {
            bottom -= HOTBAR_ZONE_HEIGHT;
        }
        return bottom;
    }

    private static float displaySpeed(Minecraft client, VehicleEntity vehicle) {
        double dx = vehicle.getX() - vehicle.xo;
        double dy = vehicle.getY() - vehicle.yo;
        double dz = vehicle.getZ() - vehicle.zo;
        float measured = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) * TICKS_PER_SECOND);

        if (smoothedSpeedVehicleId != vehicle.getId()) {
            smoothedSpeedVehicleId = vehicle.getId();
            smoothedSpeed = measured;
            wasOverspeed = false;
            wobbleAge = WOBBLE_TICKS;
            return measured;
        }
        float step = Mth.clamp(client.getDeltaFrameTime() * SPEED_SMOOTHING, 0.0f, 1.0f);
        smoothedSpeed = Mth.lerp(step, smoothedSpeed, measured);
        return smoothedSpeed;
    }

    private static Component flightTime(VehicleEntity vehicle, long media) {
        long cost = vehicle.getLastMediaCost();
        if (cost <= 0L) {
            return Component.translatable("hud.hexwright.vehicle.unknown");
        }
        long seconds = media / cost * VehicleConfig.HEX_EXECUTION_INTERVAL_TICKS / TICKS_PER_SECOND;
        return Component.literal(String.format("%d:%02d", seconds / 60L, seconds % 60L));
    }

    private static @Nullable Component statusLine(VehicleEntity vehicle, long media) {
        if (vehicle.isStalled()) {
            return Component.translatable("hud.hexwright.vehicle.stalled").withStyle(ChatFormatting.RED);
        }
        if (media <= 0L) {
            return Component.translatable("hud.hexwright.vehicle.rider_media").withStyle(ChatFormatting.YELLOW);
        }
        return null;
    }

    private static int speedRowShake(Minecraft client, boolean overspeed) {
        if (overspeed && !wasOverspeed) {
            wobbleAge = 0.0f;
        }
        wasOverspeed = overspeed;
        if (wobbleAge >= WOBBLE_TICKS) {
            return 0;
        }
        wobbleAge = Math.min(WOBBLE_TICKS, wobbleAge + client.getDeltaFrameTime());
        float progress = wobbleAge / WOBBLE_TICKS;
        float decay = 1.0f - progress;
        return Math.round(Mth.sin(progress * WOBBLE_CYCLES * Mth.TWO_PI) * WOBBLE_AMPLITUDE * decay);
    }

    private static void drawStat(
        GuiGraphics graphics, Font font, int left, int right, int y, Component label, Component value
    ) {
        drawStat(graphics, font, left, right, y, label, value, COLOR_VALUE);
    }

    private static void drawStat(
        GuiGraphics graphics, Font font, int left, int right, int y, Component label, Component value, int valueColor
    ) {
        graphics.drawString(font, label, left, y, COLOR_LABEL);
        graphics.drawString(font, value, right - font.width(value), y, valueColor);
    }

    private static void drawBar(GuiGraphics graphics, int left, int right, int y, float fraction, int color) {
        graphics.fill(left, y, right, y + BAR_HEIGHT, COLOR_BAR_TRACK);
        int filled = Math.round(Mth.clamp(fraction, 0.0f, 1.0f) * (right - left));
        if (filled > 0) {
            graphics.fill(left, y, left + filled, y + BAR_HEIGHT, color);
        }
        graphics.renderOutline(left - 1, y - 1, right - left + 2, BAR_HEIGHT + 2, COLOR_BAR_EDGE);
    }
}
