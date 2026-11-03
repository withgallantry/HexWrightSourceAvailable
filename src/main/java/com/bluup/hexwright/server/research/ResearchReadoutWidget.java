package com.bluup.hexwright.server.research;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@LDLRegister(name = "research_readout", group = ResearchWidget.EDITOR_GROUP)
public class ResearchReadoutWidget extends ResearchWidget {

    private static final int LINE = 11;
    private static final int BAR_H = 8;

    public ResearchReadoutWidget() {
        super(140, 36);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        Font font = Minecraft.getInstance().font;
        int x = getPositionX();
        int y = getPositionY();
        int width = getSizeWidth();
        ResearchSession session = session();
        TabletPuzzle puzzle = session.puzzle();

        if (puzzle == null) {
            graphics.drawString(font, Component.translatable("gui.hexwright.research.empty"), x,
                y + (getSizeHeight() - font.lineHeight) / 2, ResearchArt.DIM, false);
            return;
        }
        int top = y + 1;
        graphics.drawString(font, Component.translatable(session.damage().isArtifact()
            ? "item.hexwright.artifact_ruined_tablet" : "item.hexwright.ruined_tablet"), x, top, ResearchArt.GOLD, false);
        graphics.drawString(font, Component.translatable("gui.hexwright.research.condition", session.damage().label(),
            puzzle.damagePercent()), x, top + LINE, ResearchArt.PALE, false);

        String count = session.filledCount() + "/" + session.breakCount();
        int barW = width - font.width(count) - 4;
        int barY = top + LINE * 2 + 2;
        graphics.fill(x, barY, x + barW, barY + BAR_H, ResearchArt.TROUGH);
        graphics.renderOutline(x, barY, barW, BAR_H, ResearchArt.TROUGH_EDGE);
        int done = Math.round((barW - 4) * session.filledCount() / (float) Math.max(1, session.breakCount()));
        if (done > 0) {
            graphics.fill(x + 2, barY + 2, x + 2 + done, barY + BAR_H - 2, 0xFF000000 | ResearchArt.LIT);
        }
        graphics.drawString(font, count, x + barW + 4, barY, ResearchArt.PALE, false);
    }
}
