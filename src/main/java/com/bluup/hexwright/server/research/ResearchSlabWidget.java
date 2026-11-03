package com.bluup.hexwright.server.research;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

@LDLRegister(name = "research_slab", group = ResearchWidget.EDITOR_GROUP)
public class ResearchSlabWidget extends ResearchWidget {

    private static final int ACTION_PLACE = 1;

    private static final int HOLLOW_H = 24;
    private static final int HOLLOW_STEP = 56;
    private static final int HOLLOW_VARIANTS = 3;
    private static final int HOLLOW_TEX_W = HOLLOW_STEP * HOLLOW_VARIANTS;
    private static final int HOLLOW_TEX_H = HOLLOW_H * 4;

    public ResearchSlabWidget() {
        super(251, 312);
    }

    private int gridX() {
        return (getSizeWidth() - TabletPuzzle.COLS * ResearchArt.PITCH_X) / 2;
    }

    private int gridY() {
        return (getSizeHeight() - TabletPuzzle.ROWS * ResearchArt.PITCH_Y) / 2 + 2;
    }


    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        ResearchTrayWidget.Rubbing rubbing = ResearchTrayWidget.carried(gui);
        if (rubbing == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        int target = breakAt((int) mouseX - getPositionX(), (int) mouseY - getPositionY());
        if (target < 0) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        writeClientAction(ACTION_PLACE, buf -> {
            buf.writeByte(rubbing.traySlot());
            buf.writeByte(target);
        });
        return true;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        ServerPlayer player = sender();
        ResearchTableBlockEntity table = table();
        if (id == ACTION_PLACE && player != null && table != null) {
            table.place(player, buffer.readByte(), buffer.readByte());
        } else {
            super.handleClientAction(id, buffer);
        }
    }

    private int breakAt(int localX, int localY) {
        ResearchSession session = session();
        TabletPuzzle puzzle = session.puzzle();
        if (puzzle == null) {
            return -1;
        }
        for (int i = 0; i < puzzle.breaks().size(); i++) {
            if (session.isFilled(i)) {
                continue;
            }
            TabletPuzzle.Break gap = puzzle.breaks().get(i);
            int x = gridX() + gap.start() * ResearchArt.PITCH_X - 4;
            int y = gridY() + gap.row() * ResearchArt.PITCH_Y - 5;
            if (localX >= x && localX < x + gap.run() * ResearchArt.PITCH_X + 6
                && localY >= y && localY < y + ResearchArt.CELL + 10) {
                return i;
            }
        }
        return -1;
    }


    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int y = getPositionY();
        ResearchSession session = session();
        TabletPuzzle puzzle = session.puzzle();
        if (puzzle != null && session.damage().isArtifact()) {
            ResearchArt.tint(graphics, ResearchArt.ARTIFACT_STONE, 1f);
        }
        graphics.blit(ResearchArt.SLAB, x, y, getSizeWidth(), getSizeHeight(), 0, 0,
            ResearchArt.SLAB_TEX_W, ResearchArt.SLAB_TEX_H, ResearchArt.SLAB_TEX_W, ResearchArt.SLAB_TEX_H);
        graphics.setColor(1f, 1f, 1f, 1f);

        if (puzzle == null) {
            return;
        }
        int gridX = x + gridX();
        int gridY = y + gridY();
        for (int row = 0; row < TabletPuzzle.ROWS; row++) {
            for (int col = 0; col < TabletPuzzle.COLS; col++) {
                if (!puzzle.isMissing(row, col)) {
                    ResearchArt.carve(graphics, puzzle.glyph(row, col), gridX + col * ResearchArt.PITCH_X,
                        gridY + row * ResearchArt.PITCH_Y, puzzle.isPitted(row, col) ? 0.35f : 1.0f);
                }
            }
        }

        for (int i = 0; i < puzzle.breaks().size(); i++) {
            TabletPuzzle.Break gap = puzzle.breaks().get(i);
            int gx = gridX + gap.start() * ResearchArt.PITCH_X;
            int gy = gridY + gap.row() * ResearchArt.PITCH_Y;
            hollow(graphics, gap, i, gx - 3, gy - 4, false, 1f);
            if (session.isFilled(i)) {
                for (int j = 0; j < gap.run(); j++) {
                    ResearchArt.light(graphics, puzzle.glyph(gap.row(), gap.start() + j), gx + j * ResearchArt.PITCH_X, gy);
                }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    void drawDropTarget(GuiGraphics graphics, int mouseX, int mouseY) {
        TabletPuzzle puzzle = session().puzzle();
        int target = puzzle != null ? breakAt(mouseX - getPositionX(), mouseY - getPositionY()) : -1;
        if (target < 0) {
            return;
        }
        TabletPuzzle.Break gap = puzzle.breaks().get(target);
        int gx = getPositionX() + gridX() + gap.start() * ResearchArt.PITCH_X;
        int gy = getPositionY() + gridY() + gap.row() * ResearchArt.PITCH_Y;
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 160.0);
        hollow(graphics, gap, target, gx - 3, gy - 4, true, pulse);
    }

    @Environment(EnvType.CLIENT)
    private static void hollow(GuiGraphics graphics, TabletPuzzle.Break gap, int index, int x, int y,
                               boolean glow, float alpha) {
        int run = Mth.clamp(gap.run(), 2, 3);
        int width = run * ResearchArt.PITCH_X + 4;
        int row = (run - 2) + (glow ? 2 : 0);
        if (glow) {
            ResearchArt.tint(graphics, ResearchArt.LIT, alpha);
        }
        graphics.blit(ResearchArt.HOLLOWS, x, y, width, HOLLOW_H, (index % HOLLOW_VARIANTS) * HOLLOW_STEP, row * HOLLOW_H,
            width, HOLLOW_H, HOLLOW_TEX_W, HOLLOW_TEX_H);
        graphics.setColor(1f, 1f, 1f, 1f);
    }
}
