package com.bluup.hexwright.server.flourish.workbench;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

@LDLRegister(name = "flourish_segments", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchSegmentsWidget extends WorkbenchButtonRowWidget {

    public WorkbenchSegmentsWidget() {
        super(WorkbenchOption.SIZE, 242, 25);
    }

    static WorkbenchSegmentsWidget of(WorkbenchOption option) {
        WorkbenchSegmentsWidget widget = new WorkbenchSegmentsWidget();
        widget.option = option;
        return widget;
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected void drawButton(GuiGraphics graphics, int index, int x, int y, int width, int height, int textColour) {
        WorkbenchArt.centred(graphics, draft().choiceName(option, index), x + 2, y + (height - 8) / 2, width - 4,
            textColour);
    }
}
