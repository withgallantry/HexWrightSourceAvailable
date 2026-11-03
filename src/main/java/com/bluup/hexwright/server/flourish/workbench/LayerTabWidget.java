package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

@LDLRegister(name = "flourish_layer", group = WorkbenchWidget.EDITOR_GROUP)
public class LayerTabWidget extends WorkbenchTabWidget {

    private static final int ICON = 16;
    private static final int PENCIL = 10;

    @Configurable(name = "gui.hexwright.editor.name.flourish_layer", tips = "gui.hexwright.editor.name.flourish_layer.tips")
    @NumberRange(range = {1, FlourishDesign.MAX_LAYERS})
    private int layer = 1;

    public LayerTabWidget() {
        super(61, 22);
    }

    static LayerTabWidget of(int layer) {
        LayerTabWidget tab = new LayerTabWidget();
        tab.layer = layer;
        return tab;
    }

    @Override
    protected int index() {
        return layer - 1;
    }

    @Override
    protected int count() {
        return draft().size();
    }

    @Override
    protected int selected() {
        return draft().selected();
    }

    @Override
    protected void select(int index) {
        draft().select(index);
    }

    @Override
    protected boolean canAdd() {
        return draft().canAdd();
    }

    @Override
    protected void add() {
        draft().add();
    }

    @Override
    protected Component addLabel() {
        return Component.translatable("gui.hexwright.flourish_workbench.layer.add");
    }

    @Override
    protected boolean canRemove() {
        return draft().canRemove();
    }

    @Override
    protected void remove() {
        draft().remove();
    }

    private boolean editable() {
        FlourishDesign.Layer shown = draft().layer(index());
        return shown != null && shown.figure() != null;
    }

    private int pencilX() {
        return getPositionX() + getSizeWidth() - PENCIL - 2;
    }

    private int pencilY() {
        return getPositionY() + 2;
    }

    private boolean overPencil(double mouseX, double mouseY) {
        return editable() && isMouseOver(pencilX(), pencilY(), PENCIL, PENCIL, mouseX, mouseY);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !overPencil(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        select(index());
        draft().page(WorkbenchPage.FIGURE);
        playButtonClickSound();
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (!editable()) {
            return;
        }
        boolean hovered = overPencil(mouseX, mouseY);
        int x = pencilX();
        int y = pencilY();
        graphics.fill(x, y, x + PENCIL, y + PENCIL, hovered ? WorkbenchArt.GOLD : WorkbenchArt.DIM);
        graphics.fill(x + 1, y + 1, x + PENCIL - 1, y + PENCIL - 1, WorkbenchArt.WELL);
        int shaft = hovered ? WorkbenchArt.GOLD : WorkbenchArt.PALE;
        graphics.fill(x + 2, y + 7, x + 3, y + 8, WorkbenchArt.PALE);
        for (int i = 0; i < 5; i++) {
            graphics.fill(x + 2 + i, y + 6 - i, x + 4 + i, y + 7 - i, i == 4 ? WorkbenchArt.REFUSED : shaft);
        }
    }

    @Override
    protected List<Component> tooltip(double mouseX, double mouseY) {
        return overPencil(mouseX, mouseY)
            ? List.of(Component.translatable("gui.hexwright.flourish_workbench.layer.edit"))
            : tooltip();
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected void drawShown(GuiGraphics graphics, int x, int y, int width, int height, boolean selected) {
        FlourishDesign.Layer shown = draft().layer(index());
        int iconY = y + (height - ICON) / 2;
        if (untransformed(graphics)) {
            int[] colours = FlourishPreview.pigmentColours();
            if (shown.figure() != null) {
                FlourishPreview.drawFigure(graphics, shown.figure(), x + 3, iconY, ICON, selected, false, colours);
            } else {
                FlourishPreview.drawMotif(graphics, shown.motif(), x + 3, iconY, ICON, selected,
                    !known(shown.motif().source()), colours);
            }
        }
        int textX = x + ICON + 6;
        WorkbenchArt.left(graphics, Component.translatable(shown.motif().translationKey()), textX, y + (height - 8) / 2,
            width - (textX - x) - (editable() ? PENCIL + 4 : 3), selected ? WorkbenchArt.GOLD : WorkbenchArt.PALE);
    }

    @Override
    protected List<Component> tooltip() {
        FlourishDesign.Layer shown = draft().layer(index());
        return List.of(
            Component.translatable("gui.hexwright.flourish_workbench.layer", NUMERALS[index()],
                Component.translatable(shown.motif().translationKey())),
            Component.translatable(shown.placement().translationKey()).withStyle(ChatFormatting.GRAY),
            Component.translatable(canRemove()
                ? "gui.hexwright.flourish_workbench.layer.hint"
                : "gui.hexwright.flourish_workbench.layer.hint_last").withStyle(ChatFormatting.DARK_GRAY));
    }
}
