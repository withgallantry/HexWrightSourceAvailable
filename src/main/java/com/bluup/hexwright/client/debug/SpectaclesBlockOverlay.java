package com.bluup.hexwright.client.debug;

import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.client.accessory.WornSpectacles;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

public final class SpectaclesBlockOverlay {

    private static final int PAD = 4;
    private static final int ICON_INDENT = 18;
    private static final int TEXT_ROW = 11;
    private static final int ICON_ROW = 18;
    private static final int CROSSHAIR_OFFSET_X = 9;
    private static final int CROSSHAIR_OFFSET_Y = 10;

    private static final int COLOR_PANEL = 0xD00E1016;
    private static final int COLOR_BORDER = 0x60A8B4CC;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private SpectaclesBlockOverlay() {
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null || client.screen != null || client.options.hideGui) {
            return;
        }
        if (!WornSpectacles.worn()) {
            return;
        }
        HitResult hit = client.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockState state = level.getBlockState(blockHit.getBlockPos());
        if (state.is(HexwrightBlocks.WARDING_BOX_BLOCK) && lensInForce(player)) {
            return;
        }

        List<Pair<ItemStack, Component>> lines = HexwrightDebugLines.linesFor(
            state, blockHit.getBlockPos(), player, level, blockHit.getDirection());
        if (lines.isEmpty()) {
            return;
        }
        draw(graphics, client, lines);
    }

    private static boolean lensInForce(LocalPlayer player) {
        return player.getAttributeValue(HexAttributes.SCRY_SIGHT) > 0.0
            && player.getAttributeValue(HexAttributes.FEEBLE_MIND) <= 0.0;
    }

    private static void draw(GuiGraphics graphics, Minecraft client, List<Pair<ItemStack, Component>> lines) {
        Font font = client.font;

        int contentWidth = 0;
        int contentHeight = 0;
        for (Pair<ItemStack, Component> line : lines) {
            boolean hasIcon = !line.getFirst().isEmpty();
            contentWidth = Math.max(contentWidth,
                (hasIcon ? ICON_INDENT : 0) + font.width(line.getSecond()));
            contentHeight += hasIcon ? ICON_ROW : TEXT_ROW;
        }

        int panelWidth = contentWidth + PAD * 2;
        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int left = com.bluup.hexwright.client.vehicle.VehicleDebugOverlay.isShowing()
            ? centerX - CROSSHAIR_OFFSET_X - panelWidth
            : centerX + CROSSHAIR_OFFSET_X;
        int top = client.getWindow().getGuiScaledHeight() / 2 + CROSSHAIR_OFFSET_Y;

        graphics.fill(left, top, left + panelWidth, top + contentHeight + PAD * 2, COLOR_PANEL);
        graphics.renderOutline(left, top, panelWidth, contentHeight + PAD * 2, COLOR_BORDER);

        int x = left + PAD;
        int y = top + PAD;
        for (Pair<ItemStack, Component> line : lines) {
            ItemStack icon = line.getFirst();
            if (icon.isEmpty()) {
                graphics.drawString(font, line.getSecond(), x, y + 1, COLOR_TEXT);
                y += TEXT_ROW;
            } else {
                graphics.renderItem(icon, x, y);
                graphics.drawString(font, line.getSecond(), x + ICON_INDENT, y + 4, COLOR_TEXT);
                y += ICON_ROW;
            }
        }
    }
}
