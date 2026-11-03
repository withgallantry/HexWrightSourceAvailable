package com.bluup.hexwright.server.malediction.workbench;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.bluup.hexwright.server.flourish.workbench.WorkbenchArt;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.malediction.MaledictionBlankItem;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

@LDLRegister(name = "malediction_status", group = MaledictionWorkbenchWidget.EDITOR_GROUP)
public class MaledictionStatusWidget extends MaledictionWorkbenchWidget {

    public MaledictionStatusWidget() {
        super(176, 40);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        MaledictionSession session = session();
        if (session == null) {
            return;
        }
        int x = getPositionX();
        int y = getPositionY();
        MaledictionForm form = session.form();
        MaledictionFinish finish = session.finish();
        if (form == null || finish == null) {
            return;
        }
        graphics.renderItem(MaledictionBlankItem.fused(HexwrightItems.MALEDICTION_BLANK, form, finish), x, y);
        WorkbenchArt.left(graphics, MaledictionBlankItem.name(form, finish), x + 20, y + 4, getSizeWidth() - 20,
            0xFF000000 | MaledictionBlankItem.rarity(form, finish).color.getColor());
    }
}
