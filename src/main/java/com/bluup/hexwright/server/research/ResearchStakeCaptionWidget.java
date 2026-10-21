package com.bluup.hexwright.server.research;

import com.bluup.hexwright.client.inspiration.ClientInspiration;
import com.bluup.hexwright.server.inspiration.InspirationLevels;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@LDLRegister(name = "research_stake_caption", group = ResearchWidget.EDITOR_GROUP)
public class ResearchStakeCaptionWidget extends ResearchWidget {

    public ResearchStakeCaptionWidget() {
        super(182, 9);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        ResearchSession session = session();
        if (!session.hasTablet()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component caption;
        int colour;
        if (session.isRunning()) {
            caption = Component.translatable("gui.hexwright.research.clock", ResearchArt.clock(session.ticksLeft()));
            colour = ResearchArt.PALE;
        } else {
            int levels = session.stakeLevels();
            caption = Component.translatable(
                levels == 1 ? "gui.hexwright.research.stake.one" : "gui.hexwright.research.stake.many",
                levels, ResearchArt.clock(InspirationLevels.clockTicks(session.selected())));
            colour = !session.canSelect() ? ResearchArt.DIM
                : ClientInspiration.levels() >= levels ? ResearchArt.PALE : ResearchArt.SHORT;
        }
        graphics.drawString(font, caption, getPositionX() + (getSizeWidth() - font.width(caption)) / 2,
            getPositionY() + (getSizeHeight() - font.lineHeight) / 2 + 1, colour, false);
    }
}
