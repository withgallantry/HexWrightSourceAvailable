package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class WorkbenchPickerWidget<T> extends WorkbenchWidget {

    private static final int BAR = 3;
    private static final int BAR_CLEARANCE = BAR + 2;

    @Configurable(name = "gui.hexwright.editor.name.flourish_cell_size")
    @NumberRange(range = {16, 48})
    private int cellSize = 26;
    @Configurable(name = "gui.hexwright.editor.name.flourish_cell_gap")
    @NumberRange(range = {0, 8})
    private int cellGap = 2;
    @Configurable(name = "gui.hexwright.editor.name.flourish_cell")
    private IGuiTexture cellTexture = WorkbenchArt.box();
    @Configurable(name = "gui.hexwright.editor.name.flourish_cell_selected")
    private IGuiTexture selectedTexture = WorkbenchArt.boxLit();

    private int scroll;
    private boolean draggingBar;
    @Nullable
    private T shownChosen;

    protected WorkbenchPickerWidget(int width, int height) {
        super(width, height);
    }

    void cellSize(int cellSize) {
        this.cellSize = cellSize;
    }

    void cellGap(int cellGap) {
        this.cellGap = cellGap;
    }

    protected abstract List<T> choices();

    protected abstract @Nullable T chosen();

    protected abstract void choose(T choice);

    protected boolean locked(T choice) {
        return false;
    }

    protected boolean apart(T choice) {
        return false;
    }

    @Environment(EnvType.CLIENT)
    protected abstract void drawChoice(GuiGraphics graphics, T choice, int x, int y, int size, boolean animate,
                                       int[] colours);

    protected abstract List<Component> tooltip(T choice);

    private int pitch() {
        return cellSize + cellGap;
    }

    private int visibleRows() {
        return Math.max(1, (getSizeHeight() + cellGap) / pitch());
    }

    private int columnsIn(int width) {
        return Math.max(1, (width + cellGap) / pitch());
    }

    private int rowsOf(int columns) {
        return (choices().size() + columns - 1) / columns;
    }

    private boolean scrolls() {
        return rowsOf(columnsIn(getSizeWidth())) > visibleRows();
    }

    private int columns() {
        return columnsIn(getSizeWidth() - (scrolls() ? BAR_CLEARANCE : 0));
    }

    private int maxScroll() {
        return Math.max(0, rowsOf(columns()) - visibleRows());
    }

    private int gridWidth() {
        return columns() * pitch() - cellGap;
    }

    private int cellX(int index) {
        int room = getSizeWidth() - (scrolls() ? BAR_CLEARANCE : 0);
        return getPositionX() + (room - gridWidth()) / 2 + index % columns() * pitch();
    }

    private int cellY(int index) {
        return getPositionY() + (index / columns() - scroll) * pitch();
    }

    private boolean shown(int index) {
        int row = index / columns() - scroll;
        return row >= 0 && row < visibleRows();
    }

    private @Nullable T at(double mouseX, double mouseY) {
        List<T> choices = choices();
        for (int i = 0; i < choices.size(); i++) {
            if (shown(i) && isMouseOver(cellX(i), cellY(i), cellSize, cellSize, mouseX, mouseY)) {
                return choices.get(i);
            }
        }
        return null;
    }

    private int barX() {
        return getPositionX() + getSizeWidth() - BAR;
    }

    private int barHeight() {
        return visibleRows() * pitch() - cellGap;
    }

    private boolean overBar(double mouseX, double mouseY) {
        return scrolls() && isMouseOver(barX() - 1, getPositionY(), BAR + 1, barHeight(), mouseX, mouseY);
    }

    private void scrollToBar(double mouseY) {
        double along = (mouseY - getPositionY()) / Math.max(1, barHeight());
        scroll = Mth.clamp((int) Math.floor(along * (maxScroll() + 1)), 0, maxScroll());
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (wheelDelta == 0.0 || !isMouseOverElement(mouseX, mouseY)) {
            return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        }
        int next = Mth.clamp(scroll - (int) Math.signum(wheelDelta), 0, maxScroll());
        if (next == scroll) {
            return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        }
        scroll = next;
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overBar(mouseX, mouseY)) {
            draggingBar = true;
            scrollToBar(mouseY);
            return true;
        }
        T choice = button == 0 ? at(mouseX, mouseY) : null;
        if (choice != null) {
            if (!locked(choice)) {
                choose(choice);
                playButtonClickSound();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingBar) {
            scrollToBar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingBar) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        T chosen = chosen();
        if (chosen != shownChosen) {
            shownChosen = chosen;
            revealChosen(chosen);
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        boolean live = untransformed(graphics);
        int[] colours = FlourishPreview.pigmentColours();
        T hovered = at(mouseX, mouseY);
        List<T> choices = choices();
        for (int i = 0; i < choices.size(); i++) {
            if (!shown(i)) {
                continue;
            }
            T choice = choices.get(i);
            int x = cellX(i);
            int y = cellY(i);
            boolean locked = locked(choice);
            boolean lit = choice.equals(chosen) || !locked && choice.equals(hovered);
            (lit ? selectedTexture : cellTexture).draw(graphics, mouseX, mouseY, x, y, cellSize, cellSize);
            if (apart(choice)) {
                WorkbenchArt.refill(graphics, x, y, cellSize, cellSize, WorkbenchArt.APART_RIM,
                    lit ? WorkbenchArt.APART_LIT : WorkbenchArt.APART);
            }
            if (choice.equals(chosen)) {
                WorkbenchArt.edge(graphics, x, y, cellSize, cellSize, WorkbenchArt.GOLD);
            }
            if (locked) {
                WorkbenchArt.lock(graphics, x + (cellSize - WorkbenchArt.LOCK) / 2, y + (cellSize - WorkbenchArt.LOCK) / 2,
                    choice.equals(hovered) ? WorkbenchArt.PALE : WorkbenchArt.DIM);
            } else if (live) {
                drawChoice(graphics, choice, x + 2, y + 2, cellSize - 4, lit, colours);
            }
        }
        if (scrolls()) {
            drawBar(graphics, overBar(mouseX, mouseY) || draggingBar);
        }
    }

    private void revealChosen(@Nullable T chosen) {
        int index = chosen == null ? -1 : choices().indexOf(chosen);
        if (index < 0) {
            return;
        }
        int row = index / columns();
        scroll = Mth.clamp(scroll, row - visibleRows() + 1, row);
    }

    @Environment(EnvType.CLIENT)
    private void drawBar(GuiGraphics graphics, boolean hovered) {
        int x = barX();
        int top = getPositionY();
        int height = barHeight();
        int rows = maxScroll() + visibleRows();
        int thumb = Math.max(6, height * visibleRows() / rows);
        int thumbTop = top + (height - thumb) * scroll / Math.max(1, maxScroll());
        graphics.fill(x, top, x + BAR, top + height, WorkbenchArt.WELL);
        graphics.fill(x, thumbTop, x + BAR, thumbTop + thumb, hovered ? WorkbenchArt.PALE : WorkbenchArt.DIM);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        T choice = at(mouseX, mouseY);
        if (choice != null) {
            showTooltip(tooltip(choice));
        }
    }
}
