package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class WeaponTrailTuner {

    private static final float STEP = 0.25f;

    private static final float STEP_FINE = 0.05f;

    private static final float STEP_COARSE = 1.0f;

    private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

    private static final float MARKER_REACH = 0.18f;

    private static final int[] MARKER_COLORS = {0xFFFF4B4B, 0xFF6BE86B, 0xFF6B9CFF};

    private static final int COLOR_PANEL = 0xD00E1016;
    private static final int COLOR_BORDER = 0x60A8B4CC;
    private static final int COLOR_TITLE = 0xFFF2EFE6;
    private static final int COLOR_LABEL = 0xFF8895AA;
    private static final int COLOR_VALUE = 0xFFC8D7FF;
    private static final int COLOR_AXIS = 0xFFD9C48C;

    private static final int MARGIN = 6;
    private static final int PAD = 5;
    private static final int ROW = 10;
    private static final int PANEL_WIDTH = 210;

    private static final Map<Item, Vector3f> OFFSETS = new HashMap<>();

    @Nullable
    private static Item tuning;

    private static int axis = 1;

    private static Component notice = Component.empty();

    private WeaponTrailTuner() {
    }

    @Nullable
    static Vector3f offsetOf(Item item) {
        Vector3f offset = OFFSETS.get(item);
        return offset == null ? null : new Vector3f(offset);
    }

    public static boolean isTuning(Item item) {
        return tuning == item;
    }

    public static void renderMarker(PoseStack poseStack, MultiBufferSource buffer, Vector3f anchor) {
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        for (int drawn = 0; drawn < 3; drawn++) {
            float dx = drawn == 0 ? MARKER_REACH : 0.0f;
            float dy = drawn == 1 ? MARKER_REACH : 0.0f;
            float dz = drawn == 2 ? MARKER_REACH : 0.0f;
            int color = MARKER_COLORS[drawn];
            arm(lines, pose, normal, anchor, -dx, -dy, -dz, color);
            arm(lines, pose, normal, anchor, dx, dy, dz, color);
        }
    }

    private static void arm(VertexConsumer lines, Matrix4f pose, Matrix3f normal, Vector3f anchor,
                            float dx, float dy, float dz, int color) {
        lines.vertex(pose, anchor.x + dx, anchor.y + dy, anchor.z + dz)
            .color(color)
            .normal(normal, Math.signum(dx), Math.signum(dy), Math.signum(dz))
            .endVertex();
    }

    public static void onClientTick(Minecraft client, KeyMapping toggle, KeyMapping cycle,
                                    KeyMapping up, KeyMapping down) {
        while (toggle.consumeClick()) {
            toggle(client);
        }
        if (tuning == null) {
            cycle.consumeClick();
            up.consumeClick();
            down.consumeClick();
            return;
        }
        while (cycle.consumeClick()) {
            axis = (axis + 1) % 3;
        }
        float step = step();
        while (up.consumeClick()) {
            nudge(step);
        }
        while (down.consumeClick()) {
            nudge(-step);
        }
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        Item item = tuning;
        if (item == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return;
        }

        Vector3f offset = OFFSETS.getOrDefault(item, new Vector3f());
        Vector3f centre = WeaponModelAnchors.centreOf(item);
        Font font = client.font;

        int rows = 5;
        int height = PAD * 2 + ROW * rows;
        int x = MARGIN;
        int y = MARGIN;
        graphics.fill(x, y, x + PANEL_WIDTH, y + height, COLOR_PANEL);
        graphics.renderOutline(x, y, PANEL_WIDTH, height, COLOR_BORDER);

        int textX = x + PAD;
        int textY = y + PAD;
        graphics.drawString(font, "Trail anchor - " + BuiltInRegistries.ITEM.getKey(item).getPath(),
            textX, textY, COLOR_TITLE, false);
        textY += ROW;

        String part = item instanceof AnimatedWeapon weapon ? weapon.trailAnchorPart() : null;
        graphics.drawString(font,
            "part " + part + (centre == null ? "" : " at " + format(centre)),
            textX, textY, COLOR_LABEL, false);
        textY += ROW;

        graphics.drawString(font, "offset " + format(offset), textX, textY, COLOR_VALUE, false);
        textY += ROW;

        graphics.drawString(font,
            "axis " + AXIS_NAMES[axis] + "   step " + trim(step())
                + "   (shift fine, ctrl coarse)",
            textX, textY, COLOR_AXIS, false);
        textY += ROW;

        graphics.drawString(font, notice.getString(), textX, textY, COLOR_LABEL, false);
    }

    private static void toggle(Minecraft client) {
        if (tuning != null) {
            close(client);
            return;
        }
        if (client.player == null) {
            return;
        }
        Item item = client.player.getMainHandItem().getItem();
        if (WeaponModelAnchors.centreOf(item) == null) {
            say(client, Component.translatable("message.hexwright.trail_tuner.no_weapon")
                .withStyle(ChatFormatting.RED));
            return;
        }

        OFFSETS.computeIfAbsent(item, key -> WeaponModelAnchors.offsetOf(key));
        tuning = item;
        notice = Component.translatable("message.hexwright.trail_tuner.opened");
    }

    private static void close(Minecraft client) {
        Item item = tuning;
        tuning = null;
        if (item == null) {
            return;
        }
        Vector3f offset = OFFSETS.get(item);
        if (offset == null) {
            return;
        }

        Path path = WeaponModelAnchors.saveOffset(item, offset);
        OFFSETS.remove(item);
        say(client, Component.translatable("message.hexwright.trail_tuner.saved",
            format(offset), path.getFileName().toString()));
        say(client, Component.literal(
            "new Vector3f(" + trim(offset.x) + "f, " + trim(offset.y) + "f, " + trim(offset.z) + "f)")
            .withStyle(ChatFormatting.GRAY));
    }

    private static void nudge(float amount) {
        Item item = tuning;
        if (item == null) {
            return;
        }
        Vector3f offset = OFFSETS.computeIfAbsent(item, key -> new Vector3f());
        offset.setComponent(axis, offset.get(axis) + amount);
        notice = Component.empty();
    }

    private static float step() {
        if (Screen.hasShiftDown()) {
            return STEP_FINE;
        }
        return Screen.hasControlDown() ? STEP_COARSE : STEP;
    }

    private static void say(Minecraft client, Component message) {
        notice = message;
        if (client.player != null) {
            client.player.displayClientMessage(message, false);
        }
    }

    private static String format(Vector3f v) {
        return trim(v.x) + ", " + trim(v.y) + ", " + trim(v.z);
    }

    private static String trim(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        if (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
