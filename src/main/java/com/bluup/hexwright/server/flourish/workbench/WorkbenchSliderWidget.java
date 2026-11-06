package com.bluup.hexwright.server.flourish.workbench;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

@LDLRegister(name = "flourish_slider", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchSliderWidget extends WorkbenchFieldWidget {

    private static final int HANDLE_W = 7;

    @Configurable(name = "gui.hexwright.editor.name.flourish_slider_handle")
    private IGuiTexture handleTexture = WorkbenchArt.boxLit();

    private boolean dragging;

    public WorkbenchSliderWidget() {
        super(WorkbenchOption.DELAY, 242, 23);
    }

    static WorkbenchSliderWidget of(WorkbenchOption option) {
        WorkbenchSliderWidget widget = new WorkbenchSliderWidget();
        widget.option = option;
        return widget;
    }

    private int last() {
        return Math.max(1, draft().choices(option) - 1);
    }

    private int handleX(int notch) {
        return getPositionX() + notch * (getSizeWidth() - HANDLE_W) / last();
    }

    private boolean overTrack(double mouseX, double mouseY) {
        return isMouseOver(getPositionX(), controlY(), getSizeWidth(), controlHeight(), mouseX, mouseY);
    }

    private void dragTo(double mouseX) {
        double along = (mouseX - getPositionX() - HANDLE_W / 2.0) / Math.max(1, getSizeWidth() - HANDLE_W);
        draft().choose(option, Mth.clamp((int) Math.round(along * last()), 0, last()));
    }

    @Override
    protected Component headerValue() {
        return draft().value(option);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !overTrack(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (applies()) {
            dragging = true;
            dragTo(mouseX);
            playButtonClickSound();
        }
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            dragTo(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (wheelDelta == 0.0 || !applies() || !isMouseOverElement(mouseX, mouseY)) {
            return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        }
        int at = draft().choice(option);
        int next = Mth.clamp(at + (int) Math.signum(wheelDelta), 0, last());
        if (next == at) {
            return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        }
        draft().choose(option, next);
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected void drawControl(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        boolean applies = applies();
        int at = applies ? draft().choice(option) : 0;
        int middle = y + height / 2;
        for (int notch = 0; notch <= last(); notch++) {
            int notchX = handleX(notch) + HANDLE_W / 2;
            graphics.fill(notchX, middle + 3, notchX + 1, middle + 5, notch <= at ? WorkbenchArt.DIM : WorkbenchArt.RULE);
        }
        graphics.fill(x, middle - 2, x + width, middle + 2, WorkbenchArt.EDGE);
        graphics.fill(x + 1, middle - 1, x + width - 1, middle + 1, WorkbenchArt.WELL);
        int handleX = handleX(at);
        graphics.fill(x + 1, middle - 1, handleX + HANDLE_W / 2, middle + 1, WorkbenchArt.DIM);
        if (!applies) {
            return;
        }
        handleTexture.draw(graphics, mouseX, mouseY, handleX, y, HANDLE_W, height);
        if (dragging || overTrack(mouseX, mouseY)) {
            WorkbenchArt.edge(graphics, handleX, y, HANDLE_W, height, WorkbenchArt.GOLD);
        }
    }
}
