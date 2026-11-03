package com.bluup.hexwright.server.research;

import com.bluup.hexwright.client.inspiration.ClientInspiration;
import com.bluup.hexwright.server.inspiration.InspirationLevels;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

@LDLRegister(name = "research_gauge", group = ResearchWidget.EDITOR_GROUP)
public class ResearchGaugeWidget extends ResearchWidget {

    private static final int FLUID_FRAMES = 30;
    private static final long FLUID_FRAME_MILLIS = 100;

    public ResearchGaugeWidget() {
        super(18, 312);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int y = getPositionY();
        graphics.fill(x, y, x + getSizeWidth(), y + getSizeHeight(), ResearchArt.TROUGH);
        graphics.renderOutline(x, y, getSizeWidth(), getSizeHeight(), ResearchArt.TROUGH_EDGE);

        ResearchSession session = session();
        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = getSizeWidth() - 4;
        int innerH = getSizeHeight() - 4;
        int full = InspirationLevels.clockTicks(InspirationLevels.MAX_COMMIT);
        int shudder = session.flash() == ResearchSession.Outcome.WRONG
            ? (int) Math.round(Math.sin(System.currentTimeMillis() / 18.0) * 2) : 0;

        if (session.isRunning() || session.isLingering()) {
            int level = Mth.ceil(innerH * Mth.clamp(session.ticksLeft() / (float) full, 0f, 1f));
            fluid(graphics, innerX + shudder, innerY, innerW, innerH, level, 1f, 1f);
        } else if (session.hasTablet()) {
            int level = innerH * session.selected() / InspirationLevels.MAX_COMMIT;
            boolean affordable = ClientInspiration.levels() >= session.stakeLevels();
            fluid(graphics, innerX, innerY, innerW, innerH, level, affordable ? 1f : 0.45f, 0.5f);
        }

        for (int level = 1; level < InspirationLevels.MAX_COMMIT; level++) {
            int line = innerY + innerH - innerH * level / InspirationLevels.MAX_COMMIT;
            graphics.fill(innerX - 1, line, innerX + innerW + 1, line + 1, ResearchArt.TROUGH_EDGE);
        }
    }

    @Environment(EnvType.CLIENT)
    private static void fluid(GuiGraphics graphics, int x, int y, int width, int height, int level,
                              float greenBlue, float alpha) {
        if (level <= 0) {
            return;
        }
        int cell = ResearchArt.CELL;
        int frame = (int) ((System.currentTimeMillis() / FLUID_FRAME_MILLIS) % FLUID_FRAMES);
        graphics.setColor(1f, greenBlue, greenBlue, alpha);
        int bottom = y + height;
        for (int drawn = 0; drawn < level; drawn += cell) {
            int rows = Math.min(cell, level - drawn);
            graphics.blit(ResearchArt.FLUID, x, bottom - drawn - rows, width, rows,
                (cell - width) / 2f, frame * cell + (cell - rows), width, rows, cell, cell * FLUID_FRAMES);
        }
        graphics.setColor(1f, 1f, 1f, 1f);
        int surface = bottom - level;
        graphics.fill(x, surface, x + width, surface + 1,
            ((int) (alpha * 0xC0) << 24) | (greenBlue < 1f ? 0xF0B0B0 : 0xC6F2FF));
    }
}
