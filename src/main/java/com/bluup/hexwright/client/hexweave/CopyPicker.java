package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.server.hexweave.EmbeddedIota;
import com.bluup.hexwright.server.hexweave.ThreadEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
final class CopyPicker {

    private static final int WIDTH = 220;
    private static final int ROW = 22;
    private static final int FOOT = 12;

    private final List<Source> sources;
    private int x;
    private int y;
    private int shown;

    record Source(ItemStack stack, List<ThreadEntry> entries) {
    }

    private CopyPicker(List<Source> sources) {
        this.sources = sources;
    }

    static @Nullable CopyPicker of(Player player) {
        Inventory inventory = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            slots.add(slot);
        }
        slots.add(Inventory.SLOT_OFFHAND);
        for (int slot = Inventory.getSelectionSize(); slot < inventory.items.size(); slot++) {
            slots.add(slot);
        }
        List<Source> sources = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = inventory.getItem(slot);
            ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(stack);
            CompoundTag stored = holder == null ? null : holder.readIotaTag();
            List<ThreadEntry> entries = stored == null ? List.of() : ThreadEntry.copiedFrom(stored);
            if (!entries.isEmpty()) {
                sources.add(new Source(stack, entries));
            }
        }
        return sources.isEmpty() ? null : new CopyPicker(sources);
    }

    void place(int left, int top, int right, int bottom) {
        int room = bottom - top - LoomMenu.CHROME_HEIGHT - FOOT;
        shown = Math.max(1, Math.min(sources.size(), room / ROW));
        x = (left + right - WIDTH) / 2;
        y = (top + bottom - height()) / 2;
    }

    private int height() {
        return LoomMenu.CHROME_HEIGHT + shown * ROW + FOOT;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height();
    }

    @Nullable Source sourceAt(double mouseX, double mouseY) {
        int row = (int) Math.floor((mouseY - LoomMenu.contentY(y)) / ROW);
        return contains(mouseX, mouseY) && row >= 0 && row < shown ? sources.get(row) : null;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, LoomPalette palette, LoomMenu.Look look) {
        Font font = Minecraft.getInstance().font;
        Source hovered = sourceAt(mouseX, mouseY);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 60);
        LoomMenu.drawFrame(graphics, look, mouseX, mouseY, x, y, WIDTH, height(),
            Component.translatable("gui.hexwright.hex_loom.copy_title"), 0xFF000000 | palette.label());
        int rowX = LoomMenu.contentX(x);
        int rowWidth = WIDTH - LoomMenu.CHROME_WIDTH;
        int rowsY = LoomMenu.contentY(y);
        for (int i = 0; i < shown; i++) {
            Source source = sources.get(i);
            int rowY = rowsY + i * ROW;
            LoomMenu.drawRow(graphics, look, mouseX, mouseY, rowX, rowY, rowWidth, ROW - 2, source == hovered);
            graphics.renderItem(source.stack(), rowX + 2, rowY + 2);
            int textX = rowX + 22;
            graphics.drawString(font, font.plainSubstrByWidth(source.stack().getHoverName().getString(), rowWidth - 26),
                textX, rowY + 2, 0xFFFFFFFF);
            graphics.drawString(font, contents(source.entries()), textX, rowY + 11, 0xFF000000 | palette.label(), false);
        }
        graphics.drawString(font, Component.translatable("gui.hexwright.hex_loom.copy_hint"), rowX + 2,
            rowsY + shown * ROW + 1, 0xA0000000 | palette.label(), false);
        pose.popPose();
    }

    private static Component contents(List<ThreadEntry> entries) {
        int embedded = (int) entries.stream().filter(EmbeddedIota.class::isInstance).count();
        return embedded == 0
            ? Component.translatable("gui.hexwright.hex_loom.hover.patterns", entries.size())
            : Component.translatable("gui.hexwright.hex_loom.hover.mixed", entries.size() - embedded, embedded);
    }
}
