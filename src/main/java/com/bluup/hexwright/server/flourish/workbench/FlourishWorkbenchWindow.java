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

    private static final int LEFT_W = 250;
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 2;
    private static final int SHEET_Y = TAB_H;
    private static final int SHEET_PAD = 4;
    private static final int SHEET_INNER_W = LEFT_W - 2 * SHEET_PAD;
    private static final int TAB_W = tabWidth(FlourishDesign.MAX_LAYERS);
    private static final int RING_TAB_W = tabWidth(FlourishFigure.MAX_RINGS);
    private static final int ROW_H = 18;
    private static final int ROW_GAP = 3;
    private static final int SECTION_GAP = 6;
    private static final int CAPTION_H = 12;
    private static final int LINK_W = 72;
    private static final int OPTION_W = (SHEET_INNER_W - 2) / 2;
    private static final int TAB_CAPTION_Y = SHEET_Y + SHEET_PAD;
    private static final int ROWS_Y = TAB_CAPTION_Y + CAPTION_H + 3;
    private static final int FIELD_GAP = 5;
    private static final int SEGMENTS_H = 25;
    private static final int SLIDER_H = 23;
    private static final int SWATCHES_H = 37;
    private static final int DELAY_Y = ROWS_Y + SEGMENTS_H + FIELD_GAP;
    private static final int SHADES_Y = DELAY_Y + SLIDER_H + FIELD_GAP;
    private static final int PLACEMENT_Y = SHADES_Y + SWATCHES_H + FIELD_GAP;
    private static final int LAYER_SHEET_END = PLACEMENT_Y + ROW_H + SHEET_PAD;
    private static final int FLOURISH_Y = LAYER_SHEET_END + SECTION_GAP;
    private static final int RING_GAP = 12;
    private static final int RING_COLUMN_GAP = 10;
    private static final int RING_COLUMN_W = (SHEET_INNER_W - RING_COLUMN_GAP) / 2;
    private static final int RING_ROWS_Y = ROWS_Y + 4;
    private static final int LISTS_Y = RING_ROWS_Y + 2 * (SLIDER_H + RING_GAP);
    private static final int HUE_Y = LISTS_Y + ROW_H + RING_GAP;
    private static final int RING_SHEET_END = HUE_Y + SWATCHES_H + SHEET_PAD;
    private static final int CONTROLS_H = FLOURISH_Y + CAPTION_H + 3 + ROW_H;

    private static final int COLUMN_X = MARGIN + LEFT_W + 8;
    private static final int COLUMN_W = 182;
    private static final int WIDTH = COLUMN_X + COLUMN_W + MARGIN;

    private static final int TITLE_Y = MARGIN;
    private static final int TITLE_H = 21;
    private static final int CELL = 22;
    private static final int CELL_GAP = 2;
    private static final int SHAPES_H = 2 * CELL;
    private static final int MOTIF_ROWS = 6;
    private static final int PICKER_PAGE_Y = TITLE_Y + TITLE_H + 3;
    private static final int PICKER_PAGE_H = MOTIF_ROWS * (CELL + CELL_GAP) - CELL_GAP;
    private static final int RIGHT_OPTION_W = (COLUMN_W - 2) / 2;
    private static final int BACK_W = 130;
    private static final int BACK_H = 16;
    private static final int SHAPE_CAPTION_Y = BACK_H + 4;
    private static final int SHAPES_Y = SHAPE_CAPTION_Y + CAPTION_H + 3;
    private static final int RINGS_CAPTION_Y = SHAPES_Y + SHAPES_H + SECTION_GAP;
    private static final int FIGURE_LISTS_Y = RINGS_CAPTION_Y + CAPTION_H + 3;
    private static final int TEMPO_Y = FIGURE_LISTS_Y + ROW_H + ROW_GAP;
    private static final int EDITOR_PAGE_H = TEMPO_Y + SLIDER_H;
    private static final int STATUS_Y = PICKER_PAGE_Y + EDITOR_PAGE_H + 4;
    private static final int STATUS_H = 28;
    private static final int SLOT_Y = STATUS_Y + STATUS_H + 3;
    private static final int SLOT_FRAME = 36;
    private static final int INVENTORY_GAP = 10;
    private static final int INVENTORY_Y = SLOT_Y + SLOT_FRAME + INVENTORY_GAP;
    private static final int HEIGHT = INVENTORY_Y + 82 + MARGIN;
    private static final int CONTROLS_Y = HEIGHT - MARGIN - CONTROLS_H;
    private static final int PREVIEW_H = CONTROLS_Y - 4 - MARGIN;

    private static final int TITLE_COLOUR = 0xFFABAAAB;

    private static final WorkbenchOption[][] RING_SLIDERS = {
        {WorkbenchOption.RADIUS, WorkbenchOption.SPIN},
        {WorkbenchOption.WEIGHT, WorkbenchOption.COUNT},
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
        layers.addWidget(new ImageWidget(0, SHEET_Y, LEFT_W, LAYER_SHEET_END - SHEET_Y, WorkbenchArt.sheet()));
        for (int i = 0; i < FlourishDesign.MAX_LAYERS; i++) {
            layers.addWidget(place(LayerTabWidget.of(i + 1), SHEET_PAD + i * (TAB_W + TAB_GAP), 0, TAB_W, TAB_H));
        }
        addSection(layers, WorkbenchCaption.LAYER, WorkbenchAction.REMOVE_LAYER, SHEET_PAD, SHEET_INNER_W, TAB_CAPTION_Y);
        layers.addWidget(place(WorkbenchSegmentsWidget.of(WorkbenchOption.SIZE), SHEET_PAD, ROWS_Y, SHEET_INNER_W,
            SEGMENTS_H));
        layers.addWidget(place(WorkbenchSliderWidget.of(WorkbenchOption.DELAY), SHEET_PAD, DELAY_Y, SHEET_INNER_W,
            SLIDER_H));
        layers.addWidget(place(WorkbenchSwatchesWidget.of(WorkbenchOption.SHADE), SHEET_PAD, SHADES_Y, SHEET_INNER_W,
            SWATCHES_H));
        layers.addWidget(place(WorkbenchDropdownWidget.up(WorkbenchOption.PLACEMENT), SHEET_PAD, PLACEMENT_Y,
            SHEET_INNER_W, ROW_H));
        addSection(layers, WorkbenchCaption.FLOURISH, WorkbenchAction.RESET, SHEET_PAD, SHEET_INNER_W, FLOURISH_Y);
        layers.addWidget(place(WorkbenchDropdownWidget.up(WorkbenchOption.CHIME), SHEET_PAD, FLOURISH_Y + CAPTION_H + 3,
            OPTION_W, ROW_H));
        layers.addWidget(place(WorkbenchDropdownWidget.up(WorkbenchOption.MOTES), SHEET_PAD + OPTION_W + 2,
            FLOURISH_Y + CAPTION_H + 3, OPTION_W, ROW_H));
        root.addWidget(layers);

        WidgetGroup rings = WorkbenchPageGroup.of(WorkbenchPage.FIGURE, MARGIN, CONTROLS_Y, LEFT_W, CONTROLS_H);
        rings.addWidget(new ImageWidget(0, SHEET_Y, LEFT_W, RING_SHEET_END - SHEET_Y, WorkbenchArt.sheet()));
        for (int i = 0; i < FlourishFigure.MAX_RINGS; i++) {
            rings.addWidget(place(RingTabWidget.of(i + 1), SHEET_PAD + i * (RING_TAB_W + TAB_GAP), 0, RING_TAB_W,
                TAB_H));
        }
        addSection(rings, WorkbenchCaption.RING, WorkbenchAction.REMOVE_RING, SHEET_PAD, SHEET_INNER_W, TAB_CAPTION_Y);
        for (int row = 0; row < RING_SLIDERS.length; row++) {
            for (int column = 0; column < 2; column++) {
                rings.addWidget(place(WorkbenchSliderWidget.of(RING_SLIDERS[row][column]),
                    SHEET_PAD + column * (RING_COLUMN_W + RING_COLUMN_GAP), RING_ROWS_Y + row * (SLIDER_H + RING_GAP),
                    RING_COLUMN_W, SLIDER_H));
            }
        }
        rings.addWidget(place(WorkbenchSwatchesWidget.of(WorkbenchOption.HUE), SHEET_PAD, HUE_Y, SHEET_INNER_W,
            SWATCHES_H));
        rings.addWidget(place(WorkbenchDropdownWidget.of(WorkbenchOption.FORM), SHEET_PAD, LISTS_Y, RING_COLUMN_W,
            ROW_H));
        rings.addWidget(place(WorkbenchDropdownWidget.of(WorkbenchOption.TURN), SHEET_PAD + RING_COLUMN_W + RING_COLUMN_GAP,
            LISTS_Y, RING_COLUMN_W, ROW_H));
        root.addWidget(rings);

        root.addWidget(new ImageWidget(COLUMN_X, TITLE_Y, COLUMN_W, TITLE_H, new ResourceTexture("ldlib:textures/title.png")));
        root.addWidget(new TextTextureWidget(COLUMN_X, TITLE_Y, COLUMN_W, TITLE_H, "block.hexwright.flourish_workbench")
            .textureStyle(texture -> texture.setColor(TITLE_COLOUR)));

        WidgetGroup motifs = WorkbenchPageGroup.of(WorkbenchPage.LAYERS, COLUMN_X, PICKER_PAGE_Y, COLUMN_W,
            PICKER_PAGE_H);
        MotifPickerWidget motifPicker = new MotifPickerWidget();
        motifPicker.cellSize(CELL);
        motifPicker.cellGap(CELL_GAP);
        motifs.addWidget(place(motifPicker, 0, 0, COLUMN_W, PICKER_PAGE_H));
        root.addWidget(motifs);

        WidgetGroup shapes = WorkbenchPageGroup.of(WorkbenchPage.FIGURE, COLUMN_X, PICKER_PAGE_Y, COLUMN_W,
            EDITOR_PAGE_H);
        shapes.addWidget(place(WorkbenchActionWidget.of(WorkbenchAction.BACK), 0, 0, BACK_W, BACK_H));
        shapes.addWidget(place(WorkbenchCaptionWidget.of(WorkbenchCaption.SHAPE), 0, SHAPE_CAPTION_Y, COLUMN_W,
            CAPTION_H));
        ShapePickerWidget shapePicker = new ShapePickerWidget();
        shapePicker.cellSize(CELL);
        shapePicker.cellGap(CELL_GAP);
        shapes.addWidget(place(shapePicker, 0, SHAPES_Y, COLUMN_W, SHAPES_H + CELL_GAP));
        shapes.addWidget(place(WorkbenchCaptionWidget.of(WorkbenchCaption.RINGS), 0, RINGS_CAPTION_Y, COLUMN_W,
            CAPTION_H));
        shapes.addWidget(place(WorkbenchSliderWidget.of(WorkbenchOption.TEMPO), 0, TEMPO_Y, COLUMN_W, SLIDER_H));
        shapes.addWidget(place(WorkbenchDropdownWidget.up(WorkbenchOption.ENTRANCE), 0, FIGURE_LISTS_Y, RIGHT_OPTION_W,
            ROW_H));
        shapes.addWidget(place(WorkbenchDropdownWidget.up(WorkbenchOption.EXIT), RIGHT_OPTION_W + 2, FIGURE_LISTS_Y,
            RIGHT_OPTION_W, ROW_H));
        root.addWidget(shapes);

        WidgetGroup blankFrame = new WidgetGroup(COLUMN_X, SLOT_Y, SLOT_FRAME, SLOT_FRAME);
        blankFrame.setBackground(new ResourceTexture("ldlib:textures/menu/item_input.png"));
        blankFrame.addWidget(placeholderSlot(9, 9, BLANK_SLOT_ID));
        root.addWidget(blankFrame);
        root.addWidget(place(WorkbenchActionWidget.of(WorkbenchAction.INSCRIBE), COLUMN_X + SLOT_FRAME + 6, SLOT_Y + 2,
            COLUMN_W - SLOT_FRAME - 6, 20));
        root.addWidget(place(new WorkbenchTooltipsWidget(), COLUMN_X + SLOT_FRAME + 6, SLOT_Y + SLOT_FRAME - 10,
            COLUMN_W - SLOT_FRAME - 6, 10));
        root.addWidget(place(new WorkbenchStatusWidget(), COLUMN_X, STATUS_Y, COLUMN_W, STATUS_H));

        WidgetGroup inventory = new WidgetGroup(COLUMN_X + (COLUMN_W - 168) / 2, INVENTORY_Y, 168, 82);
        inventory.setBackground(new ResourceTexture("ldlib:textures/menu/inv_main.png"));
        for (int i = 0; i < 36; i++) {
            int y = i < 9 ? 61 : 3 + (i / 9 - 1) * 18;
            inventory.addWidget(placeholderSlot(3 + (i % 9) * 18, y, "player_inv_" + i));
        }
        root.addWidget(inventory);
        return root;
    }

    private static int tabWidth(int count) {
        return (SHEET_INNER_W - (count - 1) * TAB_GAP) / count;
    }

    private static void addSection(WidgetGroup page, WorkbenchCaption caption, WorkbenchAction action, int x, int width,
                                   int y) {
        page.addWidget(place(WorkbenchCaptionWidget.of(caption), x, y, width - LINK_W - 4, CAPTION_H));
        page.addWidget(place(WorkbenchActionWidget.link(action), x + width - LINK_W, y, LINK_W, CAPTION_H));
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
