package com.bluup.hexwright.client.signet;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.signet.SignatureMark;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

public final class SignetDrawScreen extends Screen {
    private static final int SCALE = 4;
    private static final int CANVAS_W = SignatureMark.WIDTH * SCALE;
    private static final int CANVAS_H = SignatureMark.HEIGHT * SCALE;
    private static final int PADDING = 10;
    private static final int TITLE_GAP = 20;
    private static final int BUTTON_GAP = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_SPACING = 6;
    private static final int PANEL_WIDTH = CANVAS_W + PADDING * 2;
    private static final int PANEL_HEIGHT = PADDING + TITLE_GAP + CANVAS_H + BUTTON_GAP + BUTTON_HEIGHT + PADDING;

    private static final int COLOR_PANEL = 0xFF1B1F26;
    private static final int COLOR_PANEL_BORDER = 0xFF45505C;
    private static final int COLOR_TEXT = 0xC8D7FF;
    private static final int COLOR_INK = 0xFF2B2013;

    private final InteractionHand hand;
    private final boolean[][] grid = new boolean[SignatureMark.HEIGHT][SignatureMark.WIDTH];

    private int leftPos;
    private int topPos;
    private int canvasLeft;
    private int canvasTop;

    private boolean drawing;
    private int lastX;
    private int lastY;

    public SignetDrawScreen(InteractionHand hand) {
        super(Component.translatable("gui.hexwright.artisan_signet.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
        this.canvasLeft = this.leftPos + PADDING;
        this.canvasTop = this.topPos + PADDING + TITLE_GAP;

        int buttonsY = this.canvasTop + CANVAS_H + BUTTON_GAP;
        int bx = this.canvasLeft;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.hexwright.artisan_signet.clear"), b -> clearCanvas())
            .bounds(bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        bx += BUTTON_WIDTH + BUTTON_SPACING;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.hexwright.artisan_signet.cancel"), b -> onClose())
            .bounds(bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        bx += BUTTON_WIDTH + BUTTON_SPACING;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.hexwright.artisan_signet.save"), b -> save())
            .bounds(bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void clearCanvas() {
        for (boolean[] row : this.grid) {
            java.util.Arrays.fill(row, false);
        }
    }

    private void save() {
        HexwrightNetworking.sendArtisanSignetSign(this.hand, SignatureMark.pack(this.grid));
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

        AgedParchment.render(graphics, this.canvasLeft, this.canvasTop, SignatureMark.WIDTH, SignatureMark.HEIGHT, SCALE);

        for (int y = 0; y < SignatureMark.HEIGHT; y++) {
            for (int x = 0; x < SignatureMark.WIDTH; x++) {
                if (this.grid[y][x]) {
                    int px = this.canvasLeft + x * SCALE;
                    int py = this.canvasTop + y * SCALE;
                    graphics.fill(px, py, px + SCALE, py + SCALE, COLOR_INK);
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && isOverCanvas(mouseX, mouseY)) {
            this.drawing = true;
            this.lastX = toCanvasX(mouseX);
            this.lastY = toCanvasY(mouseY);
            this.grid[this.lastY][this.lastX] = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.drawing && button == 0) {
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
            this.drawing = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            this.grid[y0][x0] = true;
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

    private int toCanvasX(double mouseX) {
        int x = (int) Math.floor((mouseX - this.canvasLeft) / SCALE);
        return Math.max(0, Math.min(SignatureMark.WIDTH - 1, x));
    }

    private int toCanvasY(double mouseY) {
        int y = (int) Math.floor((mouseY - this.canvasTop) / SCALE);
        return Math.max(0, Math.min(SignatureMark.HEIGHT - 1, y));
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
