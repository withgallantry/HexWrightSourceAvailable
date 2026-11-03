package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.bluup.hexwright.common.flourish.FlourishFigure;
import com.bluup.hexwright.common.flourish.FlourishMotif;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@LDLRegister(name = "flourish_motifs", group = WorkbenchWidget.EDITOR_GROUP)
public class MotifPickerWidget extends WorkbenchPickerWidget<FlourishMotif> {

    private static final List<FlourishMotif> MOTIFS = List.of(FlourishMotif.values());

    public MotifPickerWidget() {
        super(182, 78);
    }

    @Override
    protected List<FlourishMotif> choices() {
        return MOTIFS;
    }

    @Override
    protected @Nullable FlourishMotif chosen() {
        return draft().selectedLayer().motif();
    }

    @Override
    protected void choose(FlourishMotif motif) {
        draft().motif(motif);
    }

    @Override
    protected boolean locked(FlourishMotif motif) {
        return !known(motif.source());
    }

    @Override
    protected boolean apart(FlourishMotif motif) {
        return motif == FlourishMotif.FIGURE;
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected void drawChoice(GuiGraphics graphics, FlourishMotif motif, int x, int y, int size, boolean animate,
                              int[] colours) {
        FlourishFigure own = draft().figure();
        if (motif == FlourishMotif.FIGURE && own != null) {
            FlourishPreview.drawFigure(graphics, own, x, y, size, animate, false, colours);
        } else {
            FlourishPreview.drawMotif(graphics, motif, x, y, size, animate, false, colours);
        }
    }

    @Override
    protected List<Component> tooltip(FlourishMotif motif) {
        List<Component> lines = new ArrayList<>();
        if (motif == FlourishMotif.FIGURE) {
            lines.add(Component.translatable(motif.translationKey()).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.hexwright.flourish_workbench.motif.figure").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable(motif.translationKey()).withStyle(motif.source().rarity().color));
        lines.add(Component.translatable("gui.hexwright.flourish_workbench.motif.kind."
            + motif.kind().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_GRAY));
        if (!known(motif.source())) {
            lines.add(Component.translatable("gui.hexwright.flourish_workbench.motif.locked",
                Component.translatable("item.hexwright." + motif.source().itemId())).withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}
