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
    private IGuiTexture selectedTexture = WorkbenchArt.boxLit();

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
        } else if (button == 0 && adds()) {
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
        boolean hovered = isMouseOverElement(mouseX, mouseY);
        if (!shown()) {
            if (adds()) {
                (hovered ? selectedTexture : tabTexture).draw(graphics, mouseX, mouseY, x, y, width, height);
                WorkbenchArt.centred(graphics, addLabel(), x, y + (height - 8) / 2, width,
                    hovered ? WorkbenchArt.GOLD : WorkbenchArt.DIM);
            }
            return;
        }
        boolean selected = selected() == index();
        (selected || hovered ? selectedTexture : tabTexture).draw(graphics, mouseX, mouseY, x, y, width, height);
        drawShown(graphics, x, y, width, height, selected);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (shown() && isMouseOverElement(mouseX, mouseY)) {
            showTooltip(tooltip(mouseX, mouseY));
        }
    }
}
