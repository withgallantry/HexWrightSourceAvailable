package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.bluup.hexwright.common.flourish.FlourishFigure;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@LDLRegister(name = "flourish_shapes", group = WorkbenchWidget.EDITOR_GROUP)
public class ShapePickerWidget extends WorkbenchPickerWidget<FlourishFigure.Shape> {

    private static final List<FlourishFigure.Shape> SHAPES = List.of(FlourishFigure.Shape.values());

    public ShapePickerWidget() {
        super(182, 52);
    }

    private @Nullable FlourishFigure.Ring selectedRing() {
        return draft().ring(draft().selectedRing());
    }

    @Override
    protected List<FlourishFigure.Shape> choices() {
        return SHAPES;
    }

    @Override
    protected @Nullable FlourishFigure.Shape chosen() {
        FlourishFigure.Ring ring = selectedRing();
        return ring != null ? ring.shape() : null;
    }

    @Override
    protected void choose(FlourishFigure.Shape shape) {
        draft().shape(shape);
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected void drawChoice(GuiGraphics graphics, FlourishFigure.Shape shape, int x, int y, int size, boolean animate,
                              int[] colours) {
        FlourishFigure.Ring ring = selectedRing();
        FlourishFigure figure = ring != null
            ? FlourishFigure.of(ring.withShape(shape).withRadius(FlourishFigure.RADII))
            : FlourishFigure.of(shape);
        FlourishPreview.drawFigure(graphics, figure, x, y, size, animate, false, colours);
    }

    @Override
    protected List<Component> tooltip(FlourishFigure.Shape shape) {
        return List.of(
            Component.translatable(shape.translationKey()).withStyle(ChatFormatting.GOLD)
        );
    }
}
