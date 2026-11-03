package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.bluup.hexwright.common.flourish.FlourishFigure;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

@LDLRegister(name = "flourish_ring", group = WorkbenchWidget.EDITOR_GROUP)
public class RingTabWidget extends WorkbenchTabWidget {

    @Configurable(name = "gui.hexwright.editor.name.flourish_ring", tips = "gui.hexwright.editor.name.flourish_ring.tips")
    @NumberRange(range = {1, FlourishFigure.MAX_RINGS})
    private int ring = 1;

    public RingTabWidget() {
        super(40, 22);
    }

    static RingTabWidget of(int ring) {
        RingTabWidget tab = new RingTabWidget();
        tab.ring = ring;
        return tab;
    }

    @Override
    protected int index() {
        return ring - 1;
    }

    @Override
    protected int count() {
        return draft().ringCount();
    }

    @Override
    protected int selected() {
        return draft().selectedRing();
    }

    @Override
    protected void select(int index) {
        draft().selectRing(index);
    }

    @Override
    protected boolean canAdd() {
        return draft().canAddRing();
    }

    @Override
    protected void add() {
        draft().addRing();
    }

    @Override
    protected Component addLabel() {
        return Component.translatable("gui.hexwright.flourish_workbench.ring.add");
    }

    @Override
    protected boolean canRemove() {
        return draft().canRemoveRing();
    }

    @Override
    protected void remove() {
        draft().removeRing();
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected void drawShown(GuiGraphics graphics, int x, int y, int width, int height, boolean selected) {
        FlourishFigure.Ring shown = draft().ring(index());
        int icon = Math.min(height - 4, width - 4);
        if (untransformed(graphics)) {
            FlourishPreview.drawFigure(graphics, FlourishFigure.of(shown.withRadius(FlourishFigure.RADII)), x + 2,
                y + (height - icon) / 2, icon, selected, false, FlourishPreview.pigmentColours());
        }
        String numeral = NUMERALS[index()];
        WorkbenchArt.left(graphics, Component.literal(numeral), x + icon + 4, y + (height - 8) / 2, width - icon - 6,
            selected ? WorkbenchArt.GOLD : WorkbenchArt.PALE);
    }

    @Override
    protected List<Component> tooltip() {
        FlourishFigure.Ring shown = draft().ring(index());
        return List.of(
            Component.translatable("gui.hexwright.flourish_workbench.ring", NUMERALS[index()],
                Component.translatable(shown.shape().translationKey())),
            FlourishDraft.count(shown).copy().withStyle(ChatFormatting.GRAY),
            Component.translatable(canRemove()
                ? "gui.hexwright.flourish_workbench.ring.hint"
                : "gui.hexwright.flourish_workbench.ring.hint_last").withStyle(ChatFormatting.DARK_GRAY));
    }
}
