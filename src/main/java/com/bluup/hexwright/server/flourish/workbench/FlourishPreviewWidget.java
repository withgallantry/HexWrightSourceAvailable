package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@LDLRegister(name = "flourish_preview", group = WorkbenchWidget.EDITOR_GROUP)
public class FlourishPreviewWidget extends WorkbenchWidget {

    @Configurable(name = "gui.hexwright.editor.name.flourish_show_caster",
        tips = "gui.hexwright.editor.name.flourish_show_caster.tips")
    private boolean showCaster = true;
    @Configurable(name = "gui.hexwright.editor.name.flourish_inset", tips = "gui.hexwright.editor.name.flourish_inset.tips")
    @NumberRange(range = {0, 16})
    private int inset = 3;

    @Environment(EnvType.CLIENT)
    private FlourishPreview preview;
    private int seenReplays;
    private boolean dragging;
    private boolean dragged;

    public FlourishPreviewWidget() {
        super(250, 200);
        setBackground(WorkbenchArt.panel("internal-heavy"));
    }

    @Environment(EnvType.CLIENT)
    private FlourishPreview preview() {
        if (preview == null) {
            preview = new FlourishPreview();
        }
        return preview;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (!untransformed(graphics)) {
            return;
        }
        FlourishPreview shown = preview();
        FlourishDraft draft = draft();
        if (draft.replays() != seenReplays) {
            seenReplays = draft.replays();
            shown.replay();
        }
        shown.show(draft.design());
        shown.draw(graphics, getPositionX() + inset, getPositionY() + inset, getSizeWidth() - inset * 2,
            getSizeHeight() - inset * 2, showCaster, partialTicks);

        int textY = getPositionY() + getSizeHeight() - inset - 11;
        int textWidth = getSizeWidth() / 2 - inset - 4;
        WorkbenchArt.left(graphics, Component.translatable("gui.hexwright.flourish_workbench.preview.turn"),
            getPositionX() + inset + 4, textY, textWidth, WorkbenchArt.DIM);
        boolean hovered = isMouseOverElement(mouseX, mouseY) && !dragging;
        WorkbenchArt.right(graphics, Component.translatable("gui.hexwright.flourish_workbench.preview.replay"),
            getPositionX() + getSizeWidth() / 2, textY, textWidth, hovered ? WorkbenchArt.GOLD : WorkbenchArt.PALE);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOverElement(mouseX, mouseY)) {
            dragging = true;
            dragged = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            preview().turn(dragX, dragY);
            dragged = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            if (!dragged) {
                draft().replay();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
