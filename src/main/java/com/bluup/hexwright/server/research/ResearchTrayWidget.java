package com.bluup.hexwright.server.research;

import com.bluup.hexwright.client.progression.ClientRecipeUnlocks;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@LDLRegister(name = "research_tray", group = ResearchWidget.EDITOR_GROUP)
public class ResearchTrayWidget extends ResearchWidget {

    private static final int CARD_PITCH = ResearchArt.CARD_H + 2;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 3;
    private static final int LINE_GAP = 4;

    public record Rubbing(int traySlot) {
    }

    public ResearchTrayWidget() {
        super(182, CARD_PITCH * ResearchSession.TRAY - (CARD_PITCH - ResearchArt.CARD_H) + PAD_Y * 2);
    }

    @Environment(EnvType.CLIENT)
    static @Nullable Rubbing carried(@Nullable ModularUI gui) {
        return gui != null && gui.getModularUIGui() != null
            && gui.getModularUIGui().getDraggingElement() instanceof Rubbing rubbing ? rubbing : null;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ResearchSession session = session();
        TabletPuzzle puzzle = session.puzzle();
        if (button != 0 || puzzle == null || !session.isRunning() || gui == null
            || !gui.getModularUIContainer().getCarried().isEmpty()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int localX = (int) mouseX - getPositionX() - PAD_X;
        int localY = (int) mouseY - getPositionY() - PAD_Y;
        int slot = slotAt(puzzle, session, localX, localY);
        if (slot < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        TabletPuzzle.Scrap scrap = puzzle.deck().get(session.trayScrap(slot));
        ResearchSlabWidget slab = gui.mainGroup.getWidgetsByType(ResearchSlabWidget.class).stream()
            .findFirst().orElse(null);
        gui.getModularUIGui().setDraggingElement(new Rubbing(slot),
            new CardTexture(scrap, localX, localY - slot * CARD_PITCH, slab));
        playButtonClickSound();
        return true;
    }

    private static int slotAt(TabletPuzzle puzzle, ResearchSession session, int localX, int localY) {
        if (localX < 0 || localY < 0 || localY % CARD_PITCH >= ResearchArt.CARD_H) {
            return -1;
        }
        int slot = localY / CARD_PITCH;
        int scrap = session.trayScrap(slot);
        return scrap >= 0 && localX < ResearchArt.cardWidth(puzzle.deck().get(scrap)) ? slot : -1;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int y = getPositionY();
        int width = getSizeWidth();
        int height = getSizeHeight();
        ResearchSession session = session();
        TabletPuzzle puzzle = session.puzzle();
        ResearchArt.DESK.draw(graphics, mouseX, mouseY, x, y, width, height);

        if (puzzle != null && session.isRunning()) {
            Rubbing carried = carried(gui);
            for (int slot = 0; slot < ResearchSession.TRAY; slot++) {
                int scrap = session.trayScrap(slot);
                if (scrap >= 0 && (carried == null || carried.traySlot() != slot)) {
                    ResearchArt.drawCard(graphics, puzzle.deck().get(scrap), x + PAD_X, y + PAD_Y + slot * CARD_PITCH);
                }
            }
            return;
        }

        Font font = Minecraft.getInstance().font;
        if (session.isLingering()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(font.split(Component.translatable("gui.hexwright.research.restored")
                .withStyle(ChatFormatting.GOLD), width - 14));
            if (session.learned() != null) {
                lines.addAll(font.split(ResearchRewards.recipeName(session.learned(), session.damage()), width - 14));
            }
            int lineY = y + (height - lines.size() * font.lineHeight - LINE_GAP) / 2;
            for (FormattedCharSequence line : lines) {
                graphics.drawCenteredString(font, line, x + width / 2, lineY, 0xFFFFFFFF);
                lineY += font.lineHeight;
            }
            Component how = Component.translatable("gui.hexwright.research.fidelity."
                + ResearchRewards.fidelity(session.remaining()));
            graphics.drawCenteredString(font, how, x + width / 2, lineY + LINE_GAP, ResearchArt.PALE);
            return;
        }

        if (session.hasTablet() && nothingLeftToLearn(session.damage())) {
            Component line = Component.translatable("gui.hexwright.research.hint.nothing_new");
            int wrap = width - 14;
            graphics.drawWordWrap(font, line, x + 7, y + (height - font.wordWrapHeight(line, wrap)) / 2, wrap,
                ResearchArt.PALE);
        }
    }

    @Environment(EnvType.CLIENT)
    private static boolean nothingLeftToLearn(TabletDamage damage) {
        return ResearchRewards.pool(damage).stream().allMatch(ClientRecipeUnlocks::has);
    }

    private record CardTexture(TabletPuzzle.Scrap scrap, int grabX, int grabY, @Nullable ResearchSlabWidget slab)
        implements IGuiTexture {

        @Override
        @Environment(EnvType.CLIENT)
        public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
            RenderSystem.enableBlend();
            ResearchArt.drawCard(graphics, scrap, mouseX - grabX, mouseY - grabY);
            if (slab != null) {
                slab.drawDropTarget(graphics, mouseX, mouseY);
            }
        }
    }
}
