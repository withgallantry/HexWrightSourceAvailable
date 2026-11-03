package com.bluup.hexwright.server.flourish.workbench;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@LDLRegister(name = "flourish_caption", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchCaptionWidget extends WorkbenchWidget {

    @Configurable(name = "gui.hexwright.editor.name.flourish_caption", tips = "gui.hexwright.editor.name.flourish_caption.tips")
    private WorkbenchCaption caption = WorkbenchCaption.LAYER;

    public WorkbenchCaptionWidget() {
        super(182, 12);
    }

    static WorkbenchCaptionWidget of(WorkbenchCaption caption) {
        WorkbenchCaptionWidget widget = new WorkbenchCaptionWidget();
        widget.caption = caption;
        return widget;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        Component text = caption.text(draft());
        if (text == null) {
            return;
        }
        int x = getPositionX();
        int width = getSizeWidth();
        int textY = getPositionY() + (getSizeHeight() - 8) / 2;
        WorkbenchArt.left(graphics, text, x, textY, width, caption.faint(draft()) ? WorkbenchArt.DIM : WorkbenchArt.PALE);
        int ruleX = x + Math.min(Minecraft.getInstance().font.width(text), width) + 4;
        if (ruleX < x + width) {
            graphics.fill(ruleX, textY + 4, x + width, textY + 5, WorkbenchArt.RULE);
        }
    }
}
