package com.bluup.hexwright.server.flourish.workbench;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

abstract class WorkbenchButtonRowWidget extends WorkbenchFieldWidget {

    private static final int GAP = 2;

    @Configurable(name = "gui.hexwright.editor.name.flourish_option_face")
    private IGuiTexture faceTexture = WorkbenchArt.box();
    @Configurable(name = "gui.hexwright.editor.name.flourish_option_hover")
    private IGuiTexture hoverFaceTexture = WorkbenchArt.boxLit();

    protected WorkbenchButtonRowWidget(WorkbenchOption option, int width, int height) {
        super(option, width, height);
    }

    private int buttonX(int index, int count) {
        return getPositionX() + index * (getSizeWidth() + GAP) / count;
    }

    private int buttonWidth(int index, int count) {
        return buttonX(index + 1, count) - GAP - buttonX(index, count);
    }

    private int at(double mouseX, double mouseY) {
        int count = draft().choices(option);
        for (int index = 0; index < count; index++) {
            if (isMouseOver(buttonX(index, count), controlY(), buttonWidth(index, count), controlHeight(), mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = button == 0 ? at(mouseX, mouseY) : -1;
        if (index < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (applies()) {
            draft().choose(option, index);
            playButtonClickSound();
        }
        return true;
    }

    @Environment(EnvType.CLIENT)
    protected abstract void drawButton(GuiGraphics graphics, int index, int x, int y, int width, int height, int textColour);

    @Override
    @Environment(EnvType.CLIENT)
    protected void drawControl(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        boolean applies = applies();
        int count = draft().choices(option);
        int chosen = applies ? draft().choice(option) : -1;
        int hovered = applies ? at(mouseX, mouseY) : -1;
        for (int index = 0; index < count; index++) {
            int buttonX = buttonX(index, count);
            int buttonWidth = buttonWidth(index, count);
            boolean lit = index == chosen || index == hovered;
            (lit ? hoverFaceTexture : faceTexture).draw(graphics, mouseX, mouseY, buttonX, y, buttonWidth, height);
            if (index == chosen) {
                WorkbenchArt.edge(graphics, buttonX, y, buttonWidth, height, WorkbenchArt.GOLD);
            }
            int textColour = !applies ? WorkbenchArt.DIM : index == chosen ? WorkbenchArt.GOLD
                : index == hovered ? WorkbenchArt.PALE : WorkbenchArt.DIM;
            drawButton(graphics, index, buttonX, y, buttonWidth, height, textColour);
        }
    }
}
