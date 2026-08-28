package com.bluup.hexwright.client.reliquary;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.menu.MenuWidgets;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.bluup.hexwright.server.reliquary.HexDisplayContainer;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public record SatchelPanelLayout(int width, int height, @Nullable IGuiTexture background, List<Cell> cells) {

    public record Cell(int index, int x, int y, int width, int height, @Nullable IGuiTexture background) {
        public boolean contains(double panelRelX, double panelRelY) {
            return panelRelX >= x && panelRelX < x + width
                && panelRelY >= y && panelRelY < y + height;
        }
    }

    private static @Nullable Supplier<WidgetGroup> cachedFrom;
    private static @Nullable SatchelPanelLayout cached;

    public static @Nullable SatchelPanelLayout load() {
        Supplier<WidgetGroup> template = UiTemplates.load("satchel_inv_menu");
        if (template == null) {
            return null;
        }
        if (template == cachedFrom && cached != null) {
            return cached;
        }
        WidgetGroup root = template.get();
        if (root == null) {
            return null;
        }
        cachedFrom = template;
        cached = from(root);
        return cached;
    }

    private static SatchelPanelLayout from(WidgetGroup root) {
        Map<String, List<Widget>> widgetsById = MenuWidgets.indexById(root);
        int originX = root.getPositionX();
        int originY = root.getPositionY();

        List<Cell> cells = new ArrayList<>();
        for (int index = 0; index < HexDisplayContainer.SLOTS; index++) {
            Widget slot = MenuWidgets.firstById(widgetsById, "slot_" + index);
            if (slot == null) {
                continue;
            }
            cells.add(new Cell(
                index,
                slot.getPositionX() - originX,
                slot.getPositionY() - originY,
                slot.getSizeWidth(),
                slot.getSizeHeight(),
                slot.getBackgroundTexture()));
        }
        if (cells.isEmpty()) {
            Hexwright.LOGGER.warn("satchel_inv_menu.ui defines no slot_N widgets - backpack overlay will show no cells");
        }
        return new SatchelPanelLayout(root.getSizeWidth(), root.getSizeHeight(), root.getBackgroundTexture(), List.copyOf(cells));
    }

    public @Nullable Cell cellAt(double panelRelX, double panelRelY) {
        for (Cell cell : cells) {
            if (cell.contains(panelRelX, panelRelY)) {
                return cell;
            }
        }
        return null;
    }
}
