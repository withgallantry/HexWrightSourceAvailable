package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.common.flourish.Flourish;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@LDLRegister(name = "flourish_status", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchStatusWidget extends WorkbenchWidget {

    private static final int LINE = 10;

    public WorkbenchStatusWidget() {
        super(182, 30);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        Component text;
        int colour;
        WorkbenchSession session = session();
        Flourish missing = session != null ? session.missing() : null;
        if (missing != null) {
            text = Component.translatable("gui.hexwright.flourish_workbench.motif.locked",
                Component.translatable("item.hexwright." + missing.itemId()));
            colour = WorkbenchArt.REFUSED;
        } else if (session != null && !session.workbench().hasBlank()) {
            return;
        } else if (session != null && session.inscribed()) {
            text = Component.translatable("gui.hexwright.flourish_workbench.status.inscribed");
            colour = WorkbenchArt.PALE;
        } else {
            text = Component.translatable("gui.hexwright.flourish_workbench.status.ready");
            colour = WorkbenchArt.GOLD;
        }
        Font font = Minecraft.getInstance().font;
        int y = getPositionY();
        int bottom = y + getSizeHeight();
        for (FormattedCharSequence line : font.split(text, Math.max(1, getSizeWidth()))) {
            if (y + 8 > bottom) {
                break;
            }
            graphics.drawString(font, line, getPositionX(), y, colour, false);
            y += LINE;
        }
    }
}
