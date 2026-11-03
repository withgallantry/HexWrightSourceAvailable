package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.common.flourish.FlourishFigure;
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
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class FlourishWorkbenchWindow {

    static final String UI_PROJECT = "flourish_workbench";

    private static final String BLANK_SLOT_ID = "blank_slot";

    private static final int FRAME_RAIL = 10;
    private static final int MARGIN = 14;
    private static final int HEIGHT = 340;

    private static final int LEFT_W = 250;
    private static final int TAB_W = 61;
    private static final int RING_TAB_W = 40;
    private static final int TAB_H = 22;
    private static final int ROW_H = 18;
    private static final int ROW_GAP = 3;
    private static final int SECTION_GAP = 6;
    private static final int CAPTION_H = 12;
    private static final int LINK_W = 72;
    private static final int OPTION_W = (LEFT_W - 2) / 2;
    private static final int TAB_CAPTION_Y = TAB_H + 3;
    private static final int ROWS_Y = TAB_CAPTION_Y + CAPTION_H + 3;
    private static final int CONTROLS_H = ROWS_Y + 4 * ROW_H + 3 * ROW_GAP;
    private static final int CONTROLS_Y = HEIGHT - MARGIN - CONTROLS_H;
    private static final int PREVIEW_H = CONTROLS_Y - 4 - MARGIN;

    private static final int COLUMN_X = MARGIN + LEFT_W + 8;
    private static final int COLUMN_W = 182;
    private static final int WIDTH = COLUMN_X + COLUMN_W + MARGIN;

    private static final int TITLE_Y = MARGIN;
    private static final int TITLE_H = 21;
    private static final int CELL = 22;
    private static final int SHAPES_H = 2 * CELL;
    private static final int PICKER_PAGE_Y = TITLE_Y + TITLE_H + 3;
    private static final int PICKER_Y = CAPTION_H + 3;
    private static final int PICKER_PAGE_H = PICKER_Y + SHAPES_H + SECTION_GAP + CAPTION_H + 3 + 2 * ROW_H + ROW_GAP;
    private static final int RIGHT_OPTION_W = (COLUMN_W - 2) / 2;
    private static final int SLOT_Y = PICKER_PAGE_Y + PICKER_PAGE_H + 4;
    private static final int SLOT_FRAME = 36;
    private static final int TOOLTIPS_Y = SLOT_Y + SLOT_FRAME + 3;
    private static final int STATUS_Y = TOOLTIPS_Y + 12;
    private static final int INVENTORY_Y = HEIGHT - MARGIN - 82;

    private static final int TITLE_COLOUR = 0xFFABAAAB;

    private static final WorkbenchOption[][] LAYER_ROWS = {
        {WorkbenchOption.PLACEMENT, WorkbenchOption.SIZE},
        {WorkbenchOption.DELAY, WorkbenchOption.SHADE},
    };
    private static final WorkbenchOption[][] FLOURISH_ROWS = {
        {WorkbenchOption.CHIME, WorkbenchOption.MOTES},
    };
    private static final WorkbenchOption[][] RING_ROWS = {
        {WorkbenchOption.COUNT, WorkbenchOption.FORM},
        {WorkbenchOption.RADIUS, WorkbenchOption.WEIGHT},
        {WorkbenchOption.SPIN, WorkbenchOption.TURN},
        {WorkbenchOption.HUE},
    };

    private FlourishWorkbenchWindow() {
    }

    public static WidgetGroup build(FlourishWorkbenchBlockEntity workbench, Player player) {
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
        if (root == null || root.getWidgetsByType(FlourishPreviewWidget.class).isEmpty()) {
            Hexwright.LOGGER.warn("{}.ui has no flourish_preview; using the built-in Flourish Workbench layout", UI_PROJECT);
            return defaultLayout();
        }
        return root;
    }

    private static void bind(WidgetGroup root, FlourishWorkbenchBlockEntity workbench, Player player) {
        WorkbenchSession session = new WorkbenchSession(workbench);
        for (WorkbenchWidget widget : root.getWidgetsByType(WorkbenchWidget.class)) {
            widget.bind(session);
        }
        for (WorkbenchPageGroup group : root.getWidgetsByType(WorkbenchPageGroup.class)) {
            group.bind(session);
        }
        Map<String, List<Widget>> widgetsById = MenuWidgets.indexById(root);
        Widget slot = MenuWidgets.firstById(widgetsById, BLANK_SLOT_ID);
        if (slot instanceof SlotWidget) {
            List<Component> authored = slot.getTooltipTexts();
            MenuWidgets.bindKeySlot(slot, workbench, FlourishWorkbenchBlockEntity.SLOT, authored.isEmpty() ? List.of(
                Component.translatable("gui.hexwright.flourish_workbench.slot"),
                Component.translatable("gui.hexwright.flourish_workbench.slot.hint").withStyle(ChatFormatting.DARK_GRAY))
                : authored);
        } else {
            Hexwright.LOGGER.warn("{}.ui is missing the '{}' slot widget", UI_PROJECT, BLANK_SLOT_ID);
        }
        MenuWidgets.bindPlayerInventory(widgetsById, player);
        root.addWidget(0, session);
    }


    static WidgetGroup defaultLayout() {
        WidgetGroup root = new WidgetGroup(0, 0, WIDTH, HEIGHT);
        root.setBackground(new ResourceBorderTexture("ldlib:textures/menu/wooden-border.png", 180, 180,
            FRAME_RAIL, FRAME_RAIL));

        root.addWidget(place(new FlourishPreviewWidget(), MARGIN, MARGIN, LEFT_W, PREVIEW_H));

        WidgetGroup layers = WorkbenchPageGroup.of(WorkbenchPage.LAYERS, MARGIN, CONTROLS_Y, LEFT_W, CONTROLS_H);
        for (int i = 0; i < FlourishDesign.MAX_LAYERS; i++) {
            layers.addWidget(place(LayerTabWidget.of(i + 1), i * (TAB_W + 2), 0, TAB_W, TAB_H));
        }
        addSection(layers, WorkbenchCaption.LAYER, WorkbenchAction.REMOVE_LAYER, LEFT_W, TAB_CAPTION_Y);
        int flourishY = addRows(layers, LAYER_ROWS, OPTION_W, ROWS_Y) + SECTION_GAP;
        addSection(layers, WorkbenchCaption.FLOURISH, WorkbenchAction.RESET, LEFT_W, flourishY);
        addRows(layers, FLOURISH_ROWS, OPTION_W, flourishY + CAPTION_H + 3);
        root.addWidget(layers);

        WidgetGroup rings = WorkbenchPageGroup.of(WorkbenchPage.FIGURE, MARGIN, CONTROLS_Y, LEFT_W, CONTROLS_H);
        for (int i = 0; i < FlourishFigure.MAX_RINGS; i++) {
            rings.addWidget(place(RingTabWidget.of(i + 1), i * (RING_TAB_W + 2), 0, RING_TAB_W, TAB_H));
        }
        addSection(rings, WorkbenchCaption.RING, WorkbenchAction.REMOVE_RING, LEFT_W, TAB_CAPTION_Y);
        addRows(rings, RING_ROWS, OPTION_W, ROWS_Y);
        root.addWidget(rings);

        root.addWidget(new ImageWidget(COLUMN_X, TITLE_Y, COLUMN_W, TITLE_H, new ResourceTexture("ldlib:textures/title.png")));
        root.addWidget(new TextTextureWidget(COLUMN_X, TITLE_Y, COLUMN_W, TITLE_H, "block.hexwright.flourish_workbench")
            .textureStyle(texture -> texture.setColor(TITLE_COLOUR)));

        WidgetGroup motifs = WorkbenchPageGroup.of(WorkbenchPage.LAYERS, COLUMN_X, PICKER_PAGE_Y, COLUMN_W,
            PICKER_PAGE_H);
        motifs.addWidget(place(WorkbenchCaptionWidget.of(WorkbenchCaption.MOTIF), 0, 0, COLUMN_W, CAPTION_H));
        MotifPickerWidget motifPicker = new MotifPickerWidget();
        motifPicker.cellSize(CELL);
        motifs.addWidget(place(motifPicker, 0, PICKER_Y, COLUMN_W, PICKER_PAGE_H - PICKER_Y));
        root.addWidget(motifs);

        WidgetGroup shapes = WorkbenchPageGroup.of(WorkbenchPage.FIGURE, COLUMN_X, PICKER_PAGE_Y, COLUMN_W,
            PICKER_PAGE_H);
        addSection(shapes, WorkbenchCaption.SHAPE, WorkbenchAction.BACK, COLUMN_W, 0);
        ShapePickerWidget shapePicker = new ShapePickerWidget();
        shapePicker.cellSize(CELL);
        shapes.addWidget(place(shapePicker, 0, PICKER_Y, COLUMN_W, SHAPES_H));
        int ringsY = PICKER_Y + SHAPES_H + SECTION_GAP;
        shapes.addWidget(place(WorkbenchCaptionWidget.of(WorkbenchCaption.RINGS), 0, ringsY, COLUMN_W, CAPTION_H));
        addRows(shapes, new WorkbenchOption[][]{
            {WorkbenchOption.ENTRANCE, WorkbenchOption.EXIT},
            {WorkbenchOption.TEMPO},
        }, RIGHT_OPTION_W, ringsY + CAPTION_H + 3);
        root.addWidget(shapes);

        WidgetGroup blankFrame = new WidgetGroup(COLUMN_X, SLOT_Y, SLOT_FRAME, SLOT_FRAME);
        blankFrame.setBackground(new ResourceTexture("ldlib:textures/menu/item_input.png"));
        blankFrame.addWidget(placeholderSlot(9, 9, BLANK_SLOT_ID));
        root.addWidget(blankFrame);
        root.addWidget(place(WorkbenchActionWidget.of(WorkbenchAction.INSCRIBE), COLUMN_X + SLOT_FRAME + 6,
            SLOT_Y + (SLOT_FRAME - 20) / 2, COLUMN_W - SLOT_FRAME - 6, 20));
        root.addWidget(place(new WorkbenchTooltipsWidget(), COLUMN_X, TOOLTIPS_Y, COLUMN_W, 10));
        root.addWidget(place(new WorkbenchStatusWidget(), COLUMN_X, STATUS_Y, COLUMN_W, 30));

        WidgetGroup inventory = new WidgetGroup(COLUMN_X + (COLUMN_W - 168) / 2, INVENTORY_Y, 168, 82);
        inventory.setBackground(new ResourceTexture("ldlib:textures/menu/inv_main.png"));
        for (int i = 0; i < 36; i++) {
            int y = i < 9 ? 61 : 3 + (i / 9 - 1) * 18;
            inventory.addWidget(placeholderSlot(3 + (i % 9) * 18, y, "player_inv_" + i));
        }
        root.addWidget(inventory);
        return root;
    }

    private static void addSection(WidgetGroup page, WorkbenchCaption caption, WorkbenchAction action, int width, int y) {
        page.addWidget(place(WorkbenchCaptionWidget.of(caption), 0, y, width - LINK_W - 4, CAPTION_H));
        page.addWidget(place(WorkbenchActionWidget.link(action), width - LINK_W, y, LINK_W, CAPTION_H));
    }

    private static int addRows(WidgetGroup page, WorkbenchOption[][] rows, int optionWidth, int y) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length; column++) {
                page.addWidget(place(WorkbenchOptionWidget.of(rows[row][column]), column * (optionWidth + 2), y,
                    optionWidth, ROW_H));
            }
            y += ROW_H + ROW_GAP;
        }
        return y - ROW_GAP;
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
