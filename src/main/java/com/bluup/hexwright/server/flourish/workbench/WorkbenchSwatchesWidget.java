package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

@LDLRegister(name = "flourish_swatches", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchSwatchesWidget extends WorkbenchButtonRowWidget {

    private static final int SWATCH = 9;

    public WorkbenchSwatchesWidget() {
        super(WorkbenchOption.SHADE, 242, 37);
    }

    static WorkbenchSwatchesWidget of(WorkbenchOption option) {
        WorkbenchSwatchesWidget widget = new WorkbenchSwatchesWidget();
        widget.option = option;
        return widget;
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected void drawButton(GuiGraphics graphics, int index, int x, int y, int width, int height, int textColour) {
        int[] colours = FlourishPreview.pigmentColours();
        int top = y + (height - SWATCH - 3 - 8) / 2;
        WorkbenchArt.disc(graphics, x + (width - SWATCH) / 2, top, SWATCH, colours[index % colours.length]);
        WorkbenchArt.centred(graphics, draft().choiceName(option, index), x + 1, top + SWATCH + 3, width, textColour);
    }
}
