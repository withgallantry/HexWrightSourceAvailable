package com.bluup.hexwright.server.flourish.workbench;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

abstract class WorkbenchTabWidget extends WorkbenchWidget {

    protected static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI"};

    @Configurable(name = "gui.hexwright.editor.name.flourish_tab")
    private IGuiTexture tabTexture = WorkbenchArt.box();
    @Configurable(name = "gui.hexwright.editor.name.flourish_tab_selected")
    private IGuiTexture selectedTexture = WorkbenchArt.sheet();

    protected WorkbenchTabWidget(int width, int height) {
        super(width, height);
    }

    protected abstract int index();

    protected abstract int count();

    protected abstract int selected();

    protected abstract void select(int index);

    protected abstract boolean canAdd();

    protected abstract void add();

    protected abstract Component addLabel();

    protected abstract boolean canRemove();

    protected abstract void remove();

    @Environment(EnvType.CLIENT)
    protected abstract void drawShown(GuiGraphics graphics, int x, int y, int width, int height, boolean selected);

    protected abstract List<Component> tooltip();

    protected List<Component> tooltip(double mouseX, double mouseY) {
        return tooltip();
    }

    protected boolean shown() {
        return index() < count();
    }

    private boolean adds() {
        return index() == count() && canAdd();
    }

    private int addSize() {
        return getSizeHeight() - 2;
    }

    private boolean overAdd(double mouseX, double mouseY) {
        return isMouseOver(getPositionX(), getPositionY(), addSize(), addSize(), mouseX, mouseY);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOverElement(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (shown()) {
            if (button == 0) {
                select(index());
            } else if (button == 1 && canRemove()) {
                select(index());
                remove();
            } else {
                return true;
            }
            playButtonClickSound();
        } else if (button == 0 && adds() && overAdd(mouseX, mouseY)) {
            add();
            playButtonClickSound();
        }
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int y = getPositionY();
        int width = getSizeWidth();
        int height = getSizeHeight();
        if (!shown()) {
            if (adds()) {
                drawAdd(graphics, mouseX, mouseY, x, y, addSize());
            }
            return;
        }
        boolean selected = selected() == index();
        if (selected) {
            selectedTexture.draw(graphics, mouseX, mouseY, x, y, width, height);
            drawJoin(graphics, x, y + height, width);
        } else {
            (isMouseOverElement(mouseX, mouseY) ? selectedTexture : tabTexture)
                .draw(graphics, mouseX, mouseY, x, y, width, height + 1);
        }
        drawShown(graphics, x, y, width, height, selected);
    }

    @Environment(EnvType.CLIENT)
    private void drawAdd(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int size) {
        boolean hovered = overAdd(mouseX, mouseY);
        if (hovered) {
            tabTexture.draw(graphics, mouseX, mouseY, x, y, size, size);
        }
        WorkbenchArt.centred(graphics, Component.literal("+"), x + 1, y + (size - 8) / 2, size,
            hovered ? WorkbenchArt.GOLD : WorkbenchArt.DIM);
    }

    @Environment(EnvType.CLIENT)
    private static void drawJoin(GuiGraphics graphics, int x, int seam, int width) {
        int right = x + width;
        graphics.fill(x + 2, seam - 2, right - 2, seam + 2, WorkbenchArt.SHEET_FILL);
        graphics.fill(x + 1, seam - 2, x + 2, seam + 2, WorkbenchArt.SHEET_RIM);
        graphics.fill(right - 2, seam - 2, right - 1, seam + 2, WorkbenchArt.SHEET_RIM);
        graphics.fill(x, seam - 2, x + 1, seam + 1, WorkbenchArt.EDGE);
        graphics.fill(right - 1, seam - 2, right, seam + 1, WorkbenchArt.EDGE);
        graphics.fill(x, seam + 1, x + 1, seam + 2, WorkbenchArt.SHEET_RIM);
        graphics.fill(right - 1, seam + 1, right, seam + 2, WorkbenchArt.SHEET_RIM);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (shown() && isMouseOverElement(mouseX, mouseY)) {
            showTooltip(tooltip(mouseX, mouseY));
        } else if (adds() && overAdd(mouseX, mouseY)) {
            showTooltip(List.of(addLabel()));
        }
    }
}
