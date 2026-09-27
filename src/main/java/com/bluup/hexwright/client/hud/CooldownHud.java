package com.bluup.hexwright.client.hud;

import com.bluup.hexwright.server.item.RechargingItem;
import com.bluup.hexwright.server.talisman.WornTalismans;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CooldownHud {

    private static final int MIN_SHOWN_TICKS = 30;

    private static final int MAX_ROWS = 8;

    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_STRIP_HEIGHT = 23;
    private static final int HOTBAR_GAP = 3;
    private static final int ICON_SIZE = 16;
    private static final int SLOT_SIZE = ICON_SIZE + 2;
    private static final int ROW_STEP = SLOT_SIZE + 3;
    private static final int TEXT_GAP = 4;

    private static final int COLOR_SLOT_FRAME = 0xAA1A1328;
    private static final int COLOR_SLOT = 0x88302044;
    private static final int COLOR_TIME = 0xFFF2EFE6;
    private static final int COLOR_SWEEP = Integer.MAX_VALUE;

    private static final float DECORATION_DEPTH = 200.0F;

    private static final int TICKS_PER_SECOND = 20;

    private CooldownHud() {
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.screen != null || client.options.hideGui) {
            return;
        }
        List<Entry> entries = entries(player, partialTick);
        if (entries.isEmpty()) {
            return;
        }

        int x = client.getWindow().getGuiScaledWidth() / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
        int y = client.getWindow().getGuiScaledHeight() - HOTBAR_STRIP_HEIGHT - HOTBAR_GAP - 1 - SLOT_SIZE;
        for (Entry entry : entries) {
            drawRow(graphics, client.font, x, y, entry);
            y -= ROW_STEP;
        }
    }

    private static List<Entry> entries(LocalPlayer player, float partialTick) {
        List<Entry> entries = new ArrayList<>();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            collect(entries, player.getInventory().getItem(slot), player, partialTick);
        }
        collect(entries, player.getOffhandItem(), player, partialTick);
        for (ItemStack talisman : WornTalismans.getWorn(player)) {
            collect(entries, talisman, player, partialTick);
        }
        entries.sort(Comparator.comparingDouble((Entry entry) -> entry.recharge.remainingTicks()));
        return entries.size() > MAX_ROWS ? entries.subList(0, MAX_ROWS) : entries;
    }

    private static void collect(List<Entry> entries, ItemStack stack, LocalPlayer player, float partialTick) {
        if (!(stack.getItem() instanceof RechargingItem item)) {
            return;
        }
        RechargingItem.Recharge recharge = item.recharge(stack, player, partialTick);
        if (recharge == null || recharge.totalTicks() < MIN_SHOWN_TICKS || alreadyListed(entries, stack, recharge)) {
            return;
        }
        entries.add(new Entry(stack, recharge));
    }

    private static boolean alreadyListed(List<Entry> entries, ItemStack stack, RechargingItem.Recharge recharge) {
        for (Entry entry : entries) {
            if (entry.stack.getItem() == stack.getItem()
                && Math.round(entry.recharge.remainingTicks()) == Math.round(recharge.remainingTicks())) {
                return true;
            }
        }
        return false;
    }

    private static void drawRow(GuiGraphics graphics, Font font, int x, int y, Entry entry) {
        graphics.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, COLOR_SLOT_FRAME);
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT);

        int iconX = x + 1;
        int iconY = y + 1;
        graphics.renderFakeItem(entry.stack, iconX, iconY);

        float fraction = entry.recharge.fraction();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, DECORATION_DEPTH);
        int sweepTop = iconY + Mth.floor(ICON_SIZE * (1.0F - fraction));
        graphics.fill(RenderType.guiOverlay(), iconX, sweepTop,
            iconX + ICON_SIZE, sweepTop + Mth.ceil(ICON_SIZE * fraction), COLOR_SWEEP);
        graphics.pose().popPose();

        Component time = remaining(entry.recharge.remainingTicks());
        graphics.drawString(font, time, x + SLOT_SIZE + TEXT_GAP,
            y + (SLOT_SIZE - font.lineHeight) / 2 + 1, COLOR_TIME);
    }

    private static Component remaining(float ticks) {
        float seconds = ticks / TICKS_PER_SECOND;
        if (seconds >= 60.0f) {
            int whole = Mth.ceil(seconds);
            return Component.literal(String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60));
        }
        return Component.translatable("hud.hexwright.cooldown.seconds",
            String.format(Locale.ROOT, "%.1f", seconds));
    }

    private record Entry(ItemStack stack, RechargingItem.Recharge recharge) {
    }
}
