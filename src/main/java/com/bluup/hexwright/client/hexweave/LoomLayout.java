package com.bluup.hexwright.client.hexweave;

import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class LoomLayout {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 240;
    private static final int COLUMN_X = 6;
    private static final int COLUMN_WIDTH = 84;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CANVAS_X = 96;
    private static final int CANVAS_Y = 18;
    private static final int CANVAS_WIDTH = 296;
    private static final int CANVAS_HEIGHT = 204;
    private static final int ZOOM_SIZE = 18;

    private LoomLayout() {
    }

    static WidgetGroup build() {
        WidgetGroup root = new WidgetGroup(0, 0, WIDTH, HEIGHT);
        root.setBackground(new GuiTextureGroup(new ColorRectTexture(0xE6140E22), new ColorBorderTexture(1, 0xFF4B3A6E)));

        LoomCanvasWidget canvas = new LoomCanvasWidget();
        canvas.setSelfPosition(CANVAS_X, CANVAS_Y);
        canvas.setSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        root.addWidget(canvas);

        root.addWidget(new LabelWidget(8, 6, "gui.hexwright.hex_loom.title").setTextColor(0xFFE9DDFF));
        root.addWidget(LoomLabelWidget.of(LoomLabelWidget.Kind.SUMMARY, LoomLabelWidget.TextAlign.RIGHT, 0xFFB9A8D8,
            150, 5, WIDTH - 8 - 150, 10));
        root.addWidget(LoomLabelWidget.of(LoomLabelWidget.Kind.STATUS, LoomLabelWidget.TextAlign.LEFT, 0xFFFFFFFF,
            CANVAS_X, CANVAS_Y + CANVAS_HEIGHT + 4, CANVAS_WIDTH, 10));

        LoomModeGroupWidget weave = LoomModeGroupWidget.of(LoomModeGroupWidget.Mode.WEAVE, COLUMN_X, CANVAS_Y, COLUMN_WIDTH, 148);
        weave.addWidget(button(LoomAction.ADD, 0));
        weave.addWidget(button(LoomAction.ADD_TRIGGER, 22));
        weave.addWidget(button(LoomAction.OPEN, 52));
        weave.addWidget(button(LoomAction.DELETE, 82));
        weave.addWidget(halfButton(LoomAction.UNDO, 0, 112));
        weave.addWidget(halfButton(LoomAction.REDO, 1, 112));
        root.addWidget(weave);

        LoomModeGroupWidget thread = LoomModeGroupWidget.of(LoomModeGroupWidget.Mode.THREAD, COLUMN_X, CANVAS_Y, COLUMN_WIDTH, 148);
        thread.addWidget(button(LoomAction.BACK, 0));
        thread.addWidget(halfButton(LoomAction.UNDO, 0, 30));
        thread.addWidget(halfButton(LoomAction.REDO, 1, 30));
        thread.addWidget(button(LoomAction.REMOVE, 52));
        thread.addWidget(button(LoomAction.CLEAR, 82));
        thread.addWidget(button(LoomAction.COPY, 112));
        root.addWidget(thread);

        root.addWidget(LoomButtonWidget.of(LoomAction.FIT, COLUMN_X, 170, COLUMN_WIDTH, BUTTON_HEIGHT));
        root.addWidget(LoomButtonWidget.of(LoomAction.SAVE, COLUMN_X, HEIGHT - 46, COLUMN_WIDTH, BUTTON_HEIGHT));
        root.addWidget(LoomButtonWidget.of(LoomAction.CLOSE, COLUMN_X, HEIGHT - 24, COLUMN_WIDTH, BUTTON_HEIGHT));

        int zoomY = CANVAS_Y + CANVAS_HEIGHT - ZOOM_SIZE - 2;
        int zoomInX = CANVAS_X + CANVAS_WIDTH - ZOOM_SIZE - 2;
        root.addWidget(LoomButtonWidget.of(LoomAction.ZOOM_OUT, zoomInX - ZOOM_SIZE - 2, zoomY, ZOOM_SIZE, ZOOM_SIZE)
            .setHoverTooltips("gui.hexwright.hex_loom.zoom_out.tip"));
        root.addWidget(LoomButtonWidget.of(LoomAction.ZOOM_IN, zoomInX, zoomY, ZOOM_SIZE, ZOOM_SIZE)
            .setHoverTooltips("gui.hexwright.hex_loom.zoom_in.tip"));
        return root;
    }

    private static LoomButtonWidget button(LoomAction action, int y) {
        return LoomButtonWidget.of(action, 0, y, COLUMN_WIDTH, BUTTON_HEIGHT);
    }

    private static LoomButtonWidget halfButton(LoomAction action, int side, int y) {
        int width = (COLUMN_WIDTH - 2) / 2;
        return LoomButtonWidget.of(action, side * (width + 2), y, width, BUTTON_HEIGHT);
    }
}
