package com.bluup.hexwright.server.malediction.workbench;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.menu.MenuWidgets;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class MaledictionWorkbenchWindow {

    static final String UI_PROJECT = "malediction_workbench";

    private static final String BLANK_SLOT_ID = "blank_slot";
    private static final String FLOURISH_SLOT_ID = "flourish_slot";

    private static final int FRAME_RAIL = 10;
    private static final int MARGIN = 10;
    private static final int INNER_W = 176;
    private static final int WIDTH = INNER_W + 2 * MARGIN;
    private static final int TITLE_H = 21;
    private static final int CAPTION_H = 10;
    private static final int CELL = 22;
    private static final int FORMS_Y = MARGIN + TITLE_H + 4;
    private static final int FORM_GRID_Y = FORMS_Y + CAPTION_H + 2;
    private static final int FINISHES_Y = FORM_GRID_Y + 2 * CELL + 4;
    private static final int FINISH_GRID_Y = FINISHES_Y + CAPTION_H + 2;
    private static final int SLOT_FRAME = 36;
    private static final int FLOURISH_FRAME = 22;
    private static final int SLOT_Y = FINISH_GRID_Y + 2 * CELL + 6;
    private static final int STATUS_X = MARGIN + SLOT_FRAME + 6;
    private static final int STATUS_W = INNER_W - SLOT_FRAME - 6;
    private static final int FUSE_Y = SLOT_Y + SLOT_FRAME + 4;
    private static final int INVENTORY_Y = FUSE_Y + 26;
    private static final int HEIGHT = INVENTORY_Y + 82 + MARGIN;

    private static final int TITLE_COLOUR = 0xFFABAAAB;
    private static final int CAPTION_COLOUR = 0xFFC6B4C8;

    private MaledictionWorkbenchWindow() {
    }

    public static WidgetGroup build(MaledictionWorkbenchBlockEntity workbench, Player player) {
        WidgetGroup root = layout();
        bind(root, workbench, player);
        return root;
    }

    private static WidgetGroup layout() {
        Supplier<WidgetGroup> template = UiTemplates.loadOptional(UI_PROJECT);
        if (template == null) {
            return defaultLayout();
        }
        WidgetGroup root = template.get();
        if (root == null || root.getWidgetsByType(FormPickerWidget.class).isEmpty()) {
            Hexwright.LOGGER.warn("{}.ui has no malediction_forms; using the built-in Malediction Workbench layout",
                UI_PROJECT);
            return defaultLayout();
        }
        return root;
    }

    private static void bind(WidgetGroup root, MaledictionWorkbenchBlockEntity workbench, Player player) {
        MaledictionSession session = new MaledictionSession(workbench);
        for (MaledictionWorkbenchWidget widget : root.getWidgetsByType(MaledictionWorkbenchWidget.class)) {
            widget.bind(session);
        }
        Map<String, List<Widget>> widgetsById = MenuWidgets.indexById(root);
        bindSlot(widgetsById, BLANK_SLOT_ID, workbench, MaledictionWorkbenchBlockEntity.SLOT,
            List.of(Component.translatable("gui.hexwright.malediction_workbench.slot")));
        bindSlot(widgetsById, FLOURISH_SLOT_ID, new MaledictionFlourishSlot(workbench), 0, List.of(
            Component.translatable("gui.hexwright.malediction_workbench.flourish_slot"),
            Component.translatable("gui.hexwright.malediction_workbench.flourish_slot.hint")
                .withStyle(ChatFormatting.DARK_GRAY)));
        MenuWidgets.bindPlayerInventory(widgetsById, player);
        root.addWidget(0, session);
    }

    private static void bindSlot(Map<String, List<Widget>> widgetsById, String id, Container container, int index,
                                 List<Component> tooltip) {
        Widget slot = MenuWidgets.firstById(widgetsById, id);
        if (!(slot instanceof SlotWidget)) {
            Hexwright.LOGGER.warn("{}.ui is missing the '{}' slot widget", UI_PROJECT, id);
            return;
        }
        List<Component> authored = slot.getTooltipTexts();
        MenuWidgets.bindKeySlot(slot, container, index, authored.isEmpty() ? tooltip : authored);
    }

    static WidgetGroup defaultLayout() {
        WidgetGroup root = new WidgetGroup(0, 0, WIDTH, HEIGHT);
        root.setBackground(new ResourceBorderTexture("ldlib:textures/menu/wooden-border.png", 180, 180,
            FRAME_RAIL, FRAME_RAIL));

        root.addWidget(new ImageWidget(MARGIN, MARGIN, INNER_W, TITLE_H, new ResourceTexture("ldlib:textures/title.png")));
        root.addWidget(new TextTextureWidget(MARGIN, MARGIN, INNER_W, TITLE_H, "block.hexwright.malediction_workbench")
            .textureStyle(texture -> texture.setColor(TITLE_COLOUR)));

        root.addWidget(caption("gui.hexwright.malediction_workbench.forms", FORMS_Y));
        FormPickerWidget forms = new FormPickerWidget();
        forms.cellSize(CELL);
        root.addWidget(place(forms, MARGIN, FORM_GRID_Y, INNER_W, 2 * CELL));

        root.addWidget(caption("gui.hexwright.malediction_workbench.finishes", FINISHES_Y));
        FinishPickerWidget finishes = new FinishPickerWidget();
        finishes.cellSize(CELL);
        root.addWidget(place(finishes, MARGIN, FINISH_GRID_Y, INNER_W, 2 * CELL));

        WidgetGroup blankFrame = new WidgetGroup(MARGIN, SLOT_Y, SLOT_FRAME, SLOT_FRAME);
        blankFrame.setBackground(new ResourceTexture("ldlib:textures/menu/item_input.png"));
        blankFrame.addWidget(placeholderSlot(9, 9, BLANK_SLOT_ID));
        root.addWidget(blankFrame);
        root.addWidget(place(new MaledictionStatusWidget(), STATUS_X, SLOT_Y, STATUS_W, SLOT_FRAME));
        WidgetGroup flourishFrame = new WidgetGroup(MARGIN + (SLOT_FRAME - FLOURISH_FRAME) / 2, FUSE_Y - 1,
            FLOURISH_FRAME, FLOURISH_FRAME);
        flourishFrame.setBackground(new ResourceTexture("ldlib:textures/menu/secondary_input.png"));
        flourishFrame.addWidget(placeholderSlot(2, 2, FLOURISH_SLOT_ID));
        root.addWidget(flourishFrame);
        root.addWidget(place(new FuseWidget(), STATUS_X, FUSE_Y, STATUS_W, 20));

        WidgetGroup inventory = new WidgetGroup(MARGIN + (INNER_W - 168) / 2, INVENTORY_Y, 168, 82);
        inventory.setBackground(new ResourceTexture("ldlib:textures/menu/inv_main.png"));
        for (int i = 0; i < 36; i++) {
            int y = i < 9 ? 61 : 3 + (i / 9 - 1) * 18;
            inventory.addWidget(placeholderSlot(3 + (i % 9) * 18, y, "player_inv_" + i));
        }
        root.addWidget(inventory);
        return root;
    }

    private static Widget caption(String key, int y) {
        return new TextTextureWidget(MARGIN, y, INNER_W, CAPTION_H, key)
            .textureStyle(texture -> texture.setColor(CAPTION_COLOUR));
    }

    private static Widget place(Widget widget, int x, int y, int width, int height) {
        widget.setSelfPosition(x, y);
        widget.setSize(width, height);
        return widget;
    }

    private static SlotWidget placeholderSlot(int x, int y, String id) {
        SlotWidget slot = new SlotWidget();
        slot.setSelfPosition(x, y);
        slot.setId(id);
        slot.setBackgroundTexture(IGuiTexture.EMPTY);
        return slot;
    }
}
