package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class WorkbenchPickerWidget<T> extends WorkbenchWidget {

    @Configurable(name = "gui.hexwright.editor.name.flourish_cell_size")
    @NumberRange(range = {16, 48})
    private int cellSize = 26;
    @Configurable(name = "gui.hexwright.editor.name.flourish_cell")
    private IGuiTexture cellTexture = WorkbenchArt.box();
    @Configurable(name = "gui.hexwright.editor.name.flourish_cell_selected")
    private IGuiTexture selectedTexture = WorkbenchArt.boxLit();

    protected WorkbenchPickerWidget(int width, int height) {
        super(width, height);
    }

    void cellSize(int cellSize) {
        this.cellSize = cellSize;
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

    private int columns() {
        return Math.max(1, getSizeWidth() / cellSize);
    }

    private int cellX(int index) {
        int columns = columns();
        return getPositionX() + (getSizeWidth() - columns * cellSize) / 2 + index % columns * cellSize;
    }

    private int cellY(int index) {
        return getPositionY() + index / columns() * cellSize;
    }

    private @Nullable T at(double mouseX, double mouseY) {
        List<T> choices = choices();
        for (int i = 0; i < choices.size(); i++) {
            if (isMouseOver(cellX(i), cellY(i), cellSize, cellSize, mouseX, mouseY)) {
                return choices.get(i);
            }
        }
        return null;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        boolean live = untransformed(graphics);
        int[] colours = FlourishPreview.pigmentColours();
        T chosen = chosen();
        T hovered = at(mouseX, mouseY);
        List<T> choices = choices();
        for (int i = 0; i < choices.size(); i++) {
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
