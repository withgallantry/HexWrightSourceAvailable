package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.staff_assembly.StaffGreatSpellData;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class AmethystGreatSpellOverlay {
    private static final int ICON_SIZE = 14;
    private static final int ICON_GAP = 4;
    private static final int PANEL_PAD = 4;
    private static final int PANEL_MARGIN = 8;

    private AmethystGreatSpellOverlay() {
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY, int screenWidth, int screenHeight,
                              InteractionHand handOpenedWith) {
        Context context = resolve(handOpenedWith);
        if (context == null) {
            return;
        }

        int count = context.learned().size();
        int panelWidth = PANEL_PAD * 2 + ICON_SIZE;
        int panelHeight = panelHeight(count);
        Bounds bounds = panelBounds(count, screenWidth, screenHeight);
        int panelLeft = bounds.left();
        int panelTop = bounds.top();

        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelTop + panelHeight + 1, 0xAA1A1328);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xCC241B3A);

        for (int i = 0; i < count; i++) {
            int x = panelLeft + PANEL_PAD;
            int y = panelTop + PANEL_PAD + i * (ICON_SIZE + ICON_GAP);
            ItemStack displayScroll = context.learned().get(i).toDisplayScroll();

            drawScaledItem(graphics, displayScroll, x, y, ICON_SIZE);
            if (contains(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) {
                graphics.renderTooltip(Minecraft.getInstance().font, displayScroll, mouseX, mouseY);
            }
        }
    }

    public static boolean click(double mouseX, double mouseY, int button, int screenWidth, int screenHeight,
                                InteractionHand handOpenedWith) {
        if (button != 0) {
            return false;
        }

        Context context = resolve(handOpenedWith);
        if (context == null) {
            return false;
        }

        int count = context.learned().size();
        Bounds bounds = panelBounds(count, screenWidth, screenHeight);

        for (int i = 0; i < count; i++) {
            int x = bounds.left() + PANEL_PAD;
            int y = bounds.top() + PANEL_PAD + i * (ICON_SIZE + ICON_GAP);
            if (contains(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) {
                HexwrightNetworking.sendAmethystGreatSpellPush(handOpenedWith, i);
                return true;
            }
        }

        return false;
    }

    private record Bounds(int left, int top) {
    }

    private static int panelHeight(int count) {
        return PANEL_PAD * 2 + count * ICON_SIZE + Math.max(0, count - 1) * ICON_GAP;
    }

    private static Bounds panelBounds(int count, int screenWidth, int screenHeight) {
        int panelWidth = PANEL_PAD * 2 + ICON_SIZE;
        int panelHeight = panelHeight(count);
        int panelLeft = screenWidth - panelWidth - PANEL_MARGIN;
        int panelTop = Math.max(PANEL_MARGIN, (screenHeight - panelHeight) / 2);
        return new Bounds(panelLeft, panelTop);
    }

    private static Context resolve(InteractionHand handOpenedWith) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        ItemStack held = client.player.getItemInHand(handOpenedWith);
        if (!held.is(HexwrightItems.CONFIGURABLE_STAFF) || !StaffPowers.hasAmethystCore(held)) {
            return null;
        }

        List<StaffGreatSpellData.LearnedGreatSpell> learned = StaffGreatSpellData.getLearned(held);
        if (learned.isEmpty()) {
            return null;
        }

        return new Context(learned);
    }

    private static void drawScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, int targetSize) {
        float scale = targetSize / 16.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Context(List<StaffGreatSpellData.LearnedGreatSpell> learned) {
    }
}
