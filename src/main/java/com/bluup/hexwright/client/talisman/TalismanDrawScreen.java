package com.bluup.hexwright.client.talisman;

import com.bluup.hexwright.client.signet.AgedParchment;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.talisman.TalismanDesign;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

public final class TalismanDrawScreen extends Screen {
    private static final int SCALE = 16;
    private static final int CANVAS_W = TalismanDesign.WIDTH * SCALE;
    private static final int CANVAS_H = TalismanDesign.HEIGHT * SCALE;

    private static final int SWATCH = 16;
    private static final int SWATCH_COLS = 8;
    private static final int SWATCH_ROWS = 2;

    private static final int PADDING = 10;
    private static final int TITLE_GAP = 20;
    private static final int SECTION_GAP = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_SPACING = 4;

    private static final int PANEL_WIDTH = CANVAS_W + PADDING * 2;
    private static final int PANEL_HEIGHT = PADDING + TITLE_GAP + CANVAS_H + SECTION_GAP
        + SWATCH * SWATCH_ROWS + SECTION_GAP + BUTTON_HEIGHT + PADDING;

    private static final int COLOR_PANEL = 0xFF1B1F26;
    private static final int COLOR_PANEL_BORDER = 0xFF45505C;
    private static final int COLOR_TEXT = 0xC8D7FF;
    private static final int COLOR_SWATCH_BG = 0xFF0E1014;
    private static final int COLOR_SELECTED = 0xFFD8C486;
    private static final int COLOR_HOVER_CELL = 0x80FFFFFF;

    private final InteractionHand hand;
    private final int[][] colors = new int[TalismanDesign.HEIGHT][TalismanDesign.WIDTH];
    private final boolean[][] painted = new boolean[TalismanDesign.HEIGHT][TalismanDesign.WIDTH];

    private int selectedDye = DyeColor.BLACK.getId();

    private int leftPos;
    private int topPos;
    private int canvasLeft;
    private int canvasTop;
    private int swatchLeft;
    private int swatchTop;

    private boolean painting;
    private boolean erasing;
    private int lastX;
    private int lastY;

    public TalismanDrawScreen(InteractionHand hand) {
        super(Component.translatable("gui.hexwright.talisman_design.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
        this.canvasLeft = this.leftPos + PADDING;
        this.canvasTop = this.topPos + PADDING + TITLE_GAP;
        this.swatchLeft = this.canvasLeft;
        this.swatchTop = this.canvasTop + CANVAS_H + SECTION_GAP;

        int buttonsY = this.swatchTop + SWATCH * SWATCH_ROWS + SECTION_GAP;
        int bx = this.canvasLeft;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.hexwright.talisman_design.clear"), b -> clearCanvas())
            .bounds(bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        bx += BUTTON_WIDTH + BUTTON_SPACING;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.hexwright.talisman_design.cancel"), b -> onClose())
            .bounds(bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        bx += BUTTON_WIDTH + BUTTON_SPACING;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.hexwright.talisman_design.save"), b -> save())
            .bounds(bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void clearCanvas() {
        for (boolean[] row : this.painted) {
            java.util.Arrays.fill(row, false);
        }
    }

    private void save() {
        HexwrightNetworking.sendTalismanDesign(
            this.hand,
            TalismanDesign.pack(this.colors, this.painted),
            TalismanDesign.packMask(this.painted)
        );
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(this.leftPos, this.topPos, this.leftPos + PANEL_WIDTH, this.topPos + PANEL_HEIGHT, COLOR_PANEL);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + PANEL_WIDTH, this.topPos + 1, COLOR_PANEL_BORDER);
        graphics.fill(this.leftPos, this.topPos + PANEL_HEIGHT - 1, this.leftPos + PANEL_WIDTH, this.topPos + PANEL_HEIGHT, COLOR_PANEL_BORDER);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + 1, this.topPos + PANEL_HEIGHT, COLOR_PANEL_BORDER);
        graphics.fill(this.leftPos + PANEL_WIDTH - 1, this.topPos, this.leftPos + PANEL_WIDTH, this.topPos + PANEL_HEIGHT, COLOR_PANEL_BORDER);

        graphics.drawCenteredString(this.font, this.title, this.leftPos + PANEL_WIDTH / 2, this.topPos + 8, COLOR_TEXT);

        AgedParchment.render(graphics, this.canvasLeft, this.canvasTop, TalismanDesign.WIDTH, TalismanDesign.HEIGHT, SCALE);

        for (int y = 0; y < TalismanDesign.HEIGHT; y++) {
            for (int x = 0; x < TalismanDesign.WIDTH; x++) {
                if (this.painted[y][x]) {
                    int px = this.canvasLeft + x * SCALE;
                    int py = this.canvasTop + y * SCALE;
                    graphics.fill(px, py, px + SCALE, py + SCALE, TalismanDesign.argb(this.colors[y][x]));
                }
            }
        }

        if (isOverCanvas(mouseX, mouseY)) {
            int hx = this.canvasLeft + toCanvasX(mouseX) * SCALE;
            int hy = this.canvasTop + toCanvasY(mouseY) * SCALE;
            graphics.renderOutline(hx, hy, SCALE, SCALE, COLOR_HOVER_CELL);
        }

        renderSwatches(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        DyeColor hoveredDye = swatchAt(mouseX, mouseY);
        if (hoveredDye != null) {
            graphics.renderTooltip(this.font, new ItemStack(DyeItem.byColor(hoveredDye)), mouseX, mouseY);
        }
    }

    private void renderSwatches(GuiGraphics graphics) {
        for (DyeColor dye : DyeColor.values()) {
            int slot = dye.getId();
            int sx = this.swatchLeft + (slot % SWATCH_COLS) * SWATCH;
            int sy = this.swatchTop + (slot / SWATCH_COLS) * SWATCH;

            graphics.fill(sx, sy, sx + SWATCH, sy + SWATCH, COLOR_SWATCH_BG);
            if (slot == this.selectedDye) {
                graphics.renderOutline(sx, sy, SWATCH, SWATCH, COLOR_SELECTED);
            }
            graphics.renderItem(new ItemStack(DyeItem.byColor(dye)), sx, sy);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        DyeColor clickedDye = swatchAt(mouseX, mouseY);
        if (clickedDye != null && button == 0) {
            this.selectedDye = clickedDye.getId();
            return true;
        }

        if ((button == 0 || button == 1) && isOverCanvas(mouseX, mouseY)) {
            this.painting = button == 0;
            this.erasing = button == 1;
            this.lastX = toCanvasX(mouseX);
            this.lastY = toCanvasY(mouseY);
            apply(this.lastX, this.lastY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.painting && button == 0 || this.erasing && button == 1) {
            int x = toCanvasX(mouseX);
            int y = toCanvasY(mouseY);
            drawLine(this.lastX, this.lastY, x, y);
            this.lastX = x;
            this.lastY = y;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.painting = false;
        }
        if (button == 1) {
            this.erasing = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void apply(int x, int y) {
        if (this.erasing) {
            this.painted[y][x] = false;
        } else {
            this.painted[y][x] = true;
            this.colors[y][x] = this.selectedDye;
        }
    }

    private void drawLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            apply(x0, y0);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }


    private boolean isOverCanvas(double mouseX, double mouseY) {
        return mouseX >= this.canvasLeft && mouseX < this.canvasLeft + CANVAS_W
            && mouseY >= this.canvasTop && mouseY < this.canvasTop + CANVAS_H;
    }

    private DyeColor swatchAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - this.swatchLeft) / SWATCH);
        int row = (int) Math.floor((mouseY - this.swatchTop) / SWATCH);
        if (col < 0 || col >= SWATCH_COLS || row < 0 || row >= SWATCH_ROWS) {
            return null;
        }
        return DyeColor.byId(row * SWATCH_COLS + col);
    }

    private int toCanvasX(double mouseX) {
        int x = (int) Math.floor((mouseX - this.canvasLeft) / SCALE);
        return Math.max(0, Math.min(TalismanDesign.WIDTH - 1, x));
    }

    private int toCanvasY(double mouseY) {
        int y = (int) Math.floor((mouseY - this.canvasTop) / SCALE);
        return Math.max(0, Math.min(TalismanDesign.HEIGHT - 1, y));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
