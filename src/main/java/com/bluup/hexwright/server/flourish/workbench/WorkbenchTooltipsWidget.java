package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishWorkbenchOptions;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@LDLRegister(name = "flourish_tooltips", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchTooltipsWidget extends WorkbenchWidget {

    private static final int BOX = 9;
    private static final int GAP = 4;

    public WorkbenchTooltipsWidget() {
        super(182, 10);
    }

    private static Component label() {
        return Component.translatable("gui.hexwright.flourish_workbench.tooltips");
    }

    @Environment(EnvType.CLIENT)
    private boolean overSwitch(double mouseX, double mouseY) {
        return isMouseOverElement(mouseX, mouseY)
            && mouseX < getPositionX() + BOX + GAP + Minecraft.getInstance().font.width(label());
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !overSwitch(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        FlourishWorkbenchOptions.setTooltips(!FlourishWorkbenchOptions.tooltips());
        playButtonClickSound();
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int y = getPositionY() + (getSizeHeight() - BOX) / 2;
        boolean hovered = overSwitch(mouseX, mouseY);
        graphics.fill(x, y, x + BOX, y + BOX, hovered ? WorkbenchArt.GOLD : WorkbenchArt.DIM);
        graphics.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, WorkbenchArt.WELL);
        if (FlourishWorkbenchOptions.tooltips()) {
            graphics.fill(x + 2, y + 2, x + BOX - 2, y + BOX - 2, WorkbenchArt.GOLD);
        }
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, label(), x + BOX + GAP, y + 1, hovered ? WorkbenchArt.GOLD : WorkbenchArt.PALE, false);
    }
}
