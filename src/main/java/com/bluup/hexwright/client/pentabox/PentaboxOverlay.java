package com.bluup.hexwright.client.pentabox;

import com.bluup.hexwright.client.ldlib.widget.StaffItemDisplayWidget;
import com.bluup.hexwright.server.item.PentaboxItem;
import com.bluup.hexwright.server.pentabox.PentaboxData;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class PentaboxOverlay {
    private static final double DRAG_SENSITIVITY = 0.6;

    public static final int GRID_COLUMNS = 5;
    public static final int MAX_GRID_ROWS = 5;
    private static final int CELL_SIZE = 32;
    private static final int CELL_GAP = 4;
    private static final int STEP = CELL_SIZE + CELL_GAP;
    private static final int GRID_WIDTH = GRID_COLUMNS * STEP - CELL_GAP;

    private static int activeRows = MAX_GRID_ROWS;

    private static final int COLOR_DIM = 0xB0000000;
    private static final int COLOR_CELL = 0x80202030;
    private static final int COLOR_CELL_BORDER = 0xFF45505C;
    private static final int COLOR_CELL_HOVER = 0xC0455080;
    private static final int COLOR_CURSOR = 0xFFFFFFFF;
    private static final int COLOR_BUTTON = 0xA0202430;
    private static final int COLOR_BUTTON_HOVER = 0xD03A4A64;
    private static final int COLOR_BUTTON_BORDER = 0xFF7FA8D8;

    private static final int BUTTON_WIDTH = 118;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_MARGIN = 10;

    private static boolean active;
    private static boolean wasKeyDown;
    private static double offsetX;
    private static double offsetY;
    private static int hoveredIndex = -1;
    private static boolean hoveringOpenButton;

    public static boolean isActive() {
        return active;
    }

    public static void accumulateDrag(double dx, double dy) {
        offsetX += dx * DRAG_SENSITIVITY;
        offsetY += dy * DRAG_SENSITIVITY;
        offsetX = Mth.clamp(offsetX, -GRID_WIDTH / 2.0, GRID_WIDTH / 2.0);
        offsetY = Mth.clamp(offsetY, buttonTop(), gridHeight() / 2.0);
        updateHoverState();
    }

    private static int gridHeight() {
        return activeRows * STEP - CELL_GAP;
    }

    private static double buttonBottom() {
        return -gridHeight() / 2.0 - BUTTON_MARGIN;
    }

    private static double buttonTop() {
        return buttonBottom() - BUTTON_HEIGHT;
    }

    public static void onClientTick(Minecraft client, boolean keyDown) {
        InteractionHand hand = resolvePentaboxHand(client);
        if (hand == null) {
            reset();
            wasKeyDown = keyDown;
            return;
        }

        if (keyDown && !wasKeyDown) {
            active = true;
            offsetX = 0;
            offsetY = 0;
            hoveredIndex = -1;
            hoveringOpenButton = false;
            activeRows = resolveGridRows(client, hand);
        } else if (!keyDown && wasKeyDown) {
            int resolvedIndex = hoveredIndex;
            boolean openMenu = hoveringOpenButton;
            reset();
            if (openMenu) {
                HexwrightNetworking.sendPentaboxOpenMenu(hand);
            } else if (resolvedIndex >= 0) {
                HexwrightNetworking.sendPentaboxSelect(hand, resolvedIndex);
            }
        }

        if (!keyDown) {
            active = false;
        }
        wasKeyDown = keyDown;
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (!active) {
            return;
        }
        if (!contextValid(client)) {
            reset();
            return;
        }

        render(graphics, client);
    }

    private static boolean contextValid(Minecraft client) {
        return client.player != null
            && client.screen == null
            && resolvePentaboxHand(client) != null;
    }

    private static InteractionHand resolvePentaboxHand(Minecraft client) {
        if (client.player == null) {
            return null;
        }
        if (handHasPentaboxContext(client, InteractionHand.MAIN_HAND)) {
            return InteractionHand.MAIN_HAND;
        }
        if (handHasPentaboxContext(client, InteractionHand.OFF_HAND)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static boolean handHasPentaboxContext(Minecraft client, InteractionHand hand) {
        ItemStack held = client.player.getItemInHand(hand);
        if (held.getItem() instanceof PentaboxItem) {
            return true;
        }
        return PentaboxData.isLinkedStack(held);
    }

    private static int resolveGridRows(Minecraft client, InteractionHand hand) {
        ItemStack held = client.player.getItemInHand(hand);
        ItemStack source = PentaboxData.resolvePentaboxStack(held);
        if (source.isEmpty()) {
            return MAX_GRID_ROWS;
        }
        return Mth.clamp(PentaboxData.usableRows(source), 1, MAX_GRID_ROWS);
    }

    private static void reset() {
        active = false;
        hoveredIndex = -1;
        hoveringOpenButton = false;
    }

    private static void updateHoverState() {
        hoveredIndex = hitTest(offsetX, offsetY);
        hoveringOpenButton = isOpenButtonHit(offsetX, offsetY);
    }

    private static int hitTest(double offX, double offY) {
        double localX = offX + GRID_WIDTH / 2.0;
        double localY = offY + gridHeight() / 2.0;
        if (localX < 0 || localY < 0 || localX >= GRID_WIDTH || localY >= gridHeight()) {
            return -1;
        }
        int col = (int) (localX / STEP);
        int row = (int) (localY / STEP);
        col = Math.min(col, GRID_COLUMNS - 1);
        row = Math.min(row, activeRows - 1);
        return row * GRID_COLUMNS + col;
    }

    private static boolean isOpenButtonHit(double offX, double offY) {
        double xMin = -BUTTON_WIDTH / 2.0;
        double xMax = BUTTON_WIDTH / 2.0;
        return offX >= xMin && offX < xMax && offY >= buttonTop() && offY < buttonBottom();
    }

    private static void render(GuiGraphics graphics, Minecraft client) {
        InteractionHand hand = resolvePentaboxHand(client);
        if (hand == null) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int gridLeft = centerX - GRID_WIDTH / 2;
        int gridTop = centerY - gridHeight() / 2;
        int buttonLeft = centerX - BUTTON_WIDTH / 2;
        int buttonTop = gridTop - BUTTON_MARGIN - BUTTON_HEIGHT;

        graphics.fill(0, 0, screenWidth, screenHeight, COLOR_DIM);

        int buttonFill = hoveringOpenButton ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
        graphics.fill(buttonLeft - 1, buttonTop - 1, buttonLeft + BUTTON_WIDTH + 1, buttonTop + BUTTON_HEIGHT + 1, COLOR_BUTTON_BORDER);
        graphics.fill(buttonLeft, buttonTop, buttonLeft + BUTTON_WIDTH, buttonTop + BUTTON_HEIGHT, buttonFill);
        graphics.drawCenteredString(client.font, Component.translatable("overlay.hexwright.pentabox.open"), centerX, buttonTop + 5, 0xEAF4FF);

        ItemStack source = PentaboxData.resolvePentaboxStack(client.player.getItemInHand(hand));
        if (source.isEmpty()) {
            return;
        }
        NonNullList<ItemStack> items = PentaboxData.loadItems(source);
        for (int row = 0; row < activeRows; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                int index = row * GRID_COLUMNS + col;
                int x = gridLeft + col * STEP;
                int y = gridTop + row * STEP;
                int fill = index == hoveredIndex ? COLOR_CELL_HOVER : COLOR_CELL;

                graphics.fill(x - 1, y - 1, x + CELL_SIZE + 1, y + CELL_SIZE + 1, COLOR_CELL_BORDER);
                graphics.fill(x, y, x + CELL_SIZE, y + CELL_SIZE, fill);

                ItemStack stack = items.get(index);
                if (!stack.isEmpty()) {
                    StaffItemDisplayWidget.renderScaledItem(graphics, stack, x + 4, y + 4, CELL_SIZE - 8);
                }
            }
        }

        int cursorX = centerX + (int) Math.round(offsetX);
        int cursorY = centerY + (int) Math.round(offsetY);
        graphics.fill(cursorX - 2, cursorY - 2, cursorX + 2, cursorY + 2, COLOR_CURSOR);

        renderHoveredTooltip(graphics, client, items, cursorX, cursorY);
    }

    private static void renderHoveredTooltip(GuiGraphics graphics, Minecraft client, NonNullList<ItemStack> items, int cursorX, int cursorY) {
        if (hoveredIndex < 0 || hoveredIndex >= items.size()) {
            return;
        }
        ItemStack hovered = items.get(hoveredIndex);
        if (hovered.isEmpty()) {
            return;
        }
        graphics.renderTooltip(client.font, hovered, cursorX, cursorY);
    }

    private PentaboxOverlay() {
    }
}
