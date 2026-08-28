package com.bluup.hexwright.client.reliquary;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SatchelBackpackInventoryOverlay {

    private static final int HOVER_COLOR = 0x80FFFFFF;
    private static final int PANEL_GAP = 6;

    private static boolean visible;
    private static List<ItemStack> entries = List.of();
    private static Screen requestedForScreen;
    private static int pressedButton = -1;
    private static int pressedCell = -1;

    public static final int NO_TARGET_SLOT = -1;

    private SatchelBackpackInventoryOverlay() {
    }

    public static void onClientTick(Minecraft client) {
        if (client.player == null) {
            clear();
            return;
        }
        Screen screen = client.screen;
        if (!isSupportedScreen(screen)) {
            if (requestedForScreen != null) {
                clear();
            }
            return;
        }
        if (screen != requestedForScreen) {
            requestedForScreen = screen;
            pressedButton = -1;
            pressedCell = -1;
            HexwrightNetworking.requestSatchelBackpackView();
        }
    }

    public static void handleServerSync(boolean shouldShow, List<ItemStack> syncedEntries) {
        visible = shouldShow;
        entries = Collections.unmodifiableList(new ArrayList<>(syncedEntries));
    }

    public static void clear() {
        visible = false;
        entries = List.of();
        requestedForScreen = null;
        pressedButton = -1;
        pressedCell = -1;
    }

    private static @Nullable SatchelPanelLayout layout() {
        return SatchelPanelLayout.load();
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!visible || !isSupportedScreen(screen)) {
            return;
        }
        SatchelPanelLayout current = layout();
        if (current == null) {
            return;
        }
        PanelRect panel = panelFor(screen, current);

        if (current.background() != null) {
            current.background().draw(graphics, mouseX, mouseY, panel.x, panel.y, panel.width, panel.height);
        }

        Minecraft client = Minecraft.getInstance();
        ItemStack hoveredStack = ItemStack.EMPTY;
        for (SatchelPanelLayout.Cell cell : current.cells()) {
            int x = panel.x + cell.x();
            int y = panel.y + cell.y();
            if (cell.background() != null) {
                cell.background().draw(graphics, mouseX, mouseY, x, y, cell.width(), cell.height());
            }

            ItemStack stack = cell.index() < entries.size() ? entries.get(cell.index()) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(client.font, stack, x + 1, y + 1);
            }

            if (cell.contains(mouseX - panel.x, mouseY - panel.y)) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(0, 0, 300);
                graphics.fillGradient(x + 1, y + 1, x + cell.width() - 1, y + cell.height() - 1,
                    HOVER_COLOR, HOVER_COLOR);
                pose.popPose();
                hoveredStack = stack;
            }
        }

        if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(client.font, hoveredStack, mouseX, mouseY);
        }
    }

    public static boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        if (!visible || !isSupportedScreen(screen)) {
            return false;
        }
        SatchelPanelLayout current = layout();
        if (current == null) {
            return false;
        }
        PanelRect panel = panelFor(screen, current);
        if (!contains(panel, mouseX, mouseY)) {
            return false;
        }
        pressedButton = button;
        pressedCell = cellIndexAt(current, panel, mouseX, mouseY);
        return true;
    }

    private static boolean cursorIsClientOwned() {
        return Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen;
    }

    private static ItemStack clientCarried() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? ItemStack.EMPTY : client.player.containerMenu.getCarried();
    }

    private static void clearClientCarried() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    public static boolean mouseReleased(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button,
                                        int hoveredMenuSlot, ItemStack carried) {
        boolean ours = pressedButton == button;
        int fromCell = ours ? pressedCell : -1;
        if (ours) {
            pressedButton = -1;
            pressedCell = -1;
        }

        boolean clientCursor = cursorIsClientOwned();
        SatchelPanelLayout current = layout();
        if (visible && current != null && isSupportedScreen(screen)) {
            PanelRect panel = panelFor(screen, current);
            if (contains(panel, mouseX, mouseY)) {
                int cell = cellIndexAt(current, panel, mouseX, mouseY);
                if (cell >= 0 && (ours || !carried.isEmpty())) {
                    HexwrightNetworking.sendSatchelBackpackSlotClick(cell, NO_TARGET_SLOT, clientCursor, carried);
                    if (clientCursor && !carried.isEmpty()) {
                        clearClientCarried();
                    }
                }
                return true;
            }
        }

        if (ours && fromCell >= 0 && hoveredMenuSlot >= 0 && carried.isEmpty()) {
            HexwrightNetworking.sendSatchelBackpackSlotClick(fromCell, hoveredMenuSlot, clientCursor, carried);
            return true;
        }
        return false;
    }

    private static int cellIndexAt(SatchelPanelLayout current, PanelRect panel, double mouseX, double mouseY) {
        SatchelPanelLayout.Cell cell = current.cellAt(mouseX - panel.x, mouseY - panel.y);
        return cell == null ? -1 : cell.index();
    }

    private static boolean isSupportedScreen(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        if (screen instanceof InventoryScreen) {
            return true;
        }
        String className = screen.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("accessories")
            || className.contains("trinket")
            || className.contains("equipment")
            || className.contains("inventory");
    }

    private static boolean contains(PanelRect panel, double mouseX, double mouseY) {
        return mouseX >= panel.x && mouseX < panel.x + panel.width
            && mouseY >= panel.y && mouseY < panel.y + panel.height;
    }

    private static PanelRect panelFor(AbstractContainerScreen<?> screen, SatchelPanelLayout layout) {
        int guiLeft = (screen.width - 176) / 2;
        int guiTop = (screen.height - 166) / 2;

        int panelWidth = layout.width();
        int panelHeight = layout.height();

        int preferredX = guiLeft + 176 + PANEL_GAP;
        int left = preferredX + panelWidth <= screen.width
            ? preferredX
            : guiLeft - panelWidth - PANEL_GAP;
        int top = Math.max(0, Math.min(guiTop, screen.height - panelHeight));

        return new PanelRect(left, top, panelWidth, panelHeight);
    }

    private record PanelRect(int x, int y, int width, int height) {
    }
}
