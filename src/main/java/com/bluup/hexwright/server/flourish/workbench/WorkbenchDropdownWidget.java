package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.bluup.hexwright.common.flourish.Flourish;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@LDLRegister(name = "flourish_dropdown", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchDropdownWidget extends WorkbenchWidget {

    private static final int ROW = 12;
    private static final int BAR = 2;
    private static final String ARROW = "▾";

    @Configurable(name = "gui.hexwright.editor.name.flourish_option", tips = "gui.hexwright.editor.name.flourish_option.tips")
    private WorkbenchOption option = WorkbenchOption.FORM;
    @Configurable(name = "gui.hexwright.editor.name.flourish_dropdown_up", tips = "gui.hexwright.editor.name.flourish_dropdown_up.tips")
    private boolean opensUp;
    @Configurable(name = "gui.hexwright.editor.name.flourish_dropdown_rows", tips = "gui.hexwright.editor.name.flourish_dropdown_rows.tips")
    @NumberRange(range = {2, 20})
    private int maxRows = 8;
    @Configurable(name = "gui.hexwright.editor.name.flourish_option_face")
    private IGuiTexture faceTexture = WorkbenchArt.box();
    @Configurable(name = "gui.hexwright.editor.name.flourish_option_hover")
    private IGuiTexture hoverFaceTexture = WorkbenchArt.boxLit();

    private int scroll;

    public WorkbenchDropdownWidget() {
        super(120, 18);
    }

    static WorkbenchDropdownWidget of(WorkbenchOption option) {
        WorkbenchDropdownWidget widget = new WorkbenchDropdownWidget();
        widget.option = option;
        return widget;
    }

    static WorkbenchDropdownWidget up(WorkbenchOption option) {
        WorkbenchDropdownWidget widget = of(option);
        widget.opensUp = true;
        return widget;
    }

    private boolean open() {
        return draft().openList() == option;
    }

    private int rows() {
        return Math.min(draft().choices(option), maxRows);
    }

    private int maxScroll() {
        return Math.max(0, draft().choices(option) - rows());
    }

    private int listY() {
        return opensUp ? getPositionY() - listHeight() + 1 : getPositionY() + getSizeHeight() - 1;
    }

    private int listHeight() {
        return rows() * ROW + 4;
    }

    private boolean overList(double mouseX, double mouseY) {
        return open() && isMouseOver(getPositionX(), listY(), getSizeWidth(), listHeight(), mouseX, mouseY);
    }

    private int at(double mouseX, double mouseY) {
        if (!open() || !isMouseOver(getPositionX(), listY() + 2, getSizeWidth(), listHeight() - 4, mouseX, mouseY)) {
            return -1;
        }
        return scroll + (int) ((mouseY - listY() - 2) / ROW);
    }

    private boolean refused(int index) {
        return option == WorkbenchOption.CHIME && index > 0 && !known(Flourish.values()[index - 1]);
    }

    private @Nullable Flourish chosenChime() {
        return option == WorkbenchOption.CHIME ? draft().chime() : null;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean overButton = isMouseOverElement(mouseX, mouseY);
        if (open()) {
            int index = button == 0 ? at(mouseX, mouseY) : -1;
            draft().openList(null);
            if (index >= 0 && option == WorkbenchOption.CHIME) {
                draft().choose(option, index);
                FlourishPreview.chime(draft().chime());
            } else {
                if (index >= 0) {
                    draft().choose(option, index);
                }
                playButtonClickSound();
            }
            return true;
        }
        if (button != 0 || !overButton) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (draft().applies(option) && draft().choices(option) > 0) {
            draft().openList(option);
            scroll = Mth.clamp(draft().choice(option) - rows() / 2, 0, maxScroll());
            playButtonClickSound();
        }
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (wheelDelta == 0.0 || !overList(mouseX, mouseY)) {
            return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(wheelDelta), 0, maxScroll());
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int y = getPositionY();
        int width = getSizeWidth();
        int height = getSizeHeight();
        boolean applies = draft().applies(option);
        boolean lit = applies && (open() || isMouseOverElement(mouseX, mouseY));
        (lit ? hoverFaceTexture : faceTexture).draw(graphics, mouseX, mouseY, x, y, width, height);
        int textY = y + (height - 8) / 2;
        Flourish chime = chosenChime();
        int valueColour = !applies ? WorkbenchArt.DIM
            : chime != null && !known(chime) ? WorkbenchArt.REFUSED : WorkbenchArt.GOLD;
        Component labelled = option.label().copy().withStyle(style -> style.withColor(WorkbenchArt.DIM & 0xFFFFFF))
            .append(Component.literal(" ").append(draft().value(option))
                .withStyle(style -> style.withColor(valueColour & 0xFFFFFF)));
        int room = width - 16;
        WorkbenchArt.left(graphics, WorkbenchArt.fits(labelled, room) ? labelled : draft().value(option), x + 5, textY,
            room, valueColour);
        WorkbenchArt.right(graphics, Component.literal(ARROW), x, textY, width - 5, lit ? WorkbenchArt.GOLD : WorkbenchArt.DIM);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (open()) {
            drawList(graphics, mouseX, mouseY);
        } else if (option.hasTooltip() && isMouseOverElement(mouseX, mouseY)) {
            showTooltip(List.of(
                option.label().copy().withStyle(ChatFormatting.GOLD),
                option.hint().copy().withStyle(ChatFormatting.GRAY)));
        }
    }

    @Environment(EnvType.CLIENT)
    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = getPositionX();
        int y = listY();
        int width = getSizeWidth();
        int chosen = draft().choice(option);
        int hovered = at(mouseX, mouseY);
        boolean scrolls = maxScroll() > 0;
        int textWidth = width - 10 - (scrolls ? BAR + 2 : 0);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        faceTexture.draw(graphics, mouseX, mouseY, x, y, width, listHeight());
        for (int row = 0; row < rows(); row++) {
            int index = scroll + row;
            int rowY = y + 2 + row * ROW;
            if (index == hovered) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + ROW, WorkbenchArt.LIT_FILL);
            }
            int colour = refused(index) ? WorkbenchArt.REFUSED
                : index == chosen ? WorkbenchArt.GOLD : index == hovered ? WorkbenchArt.PALE : WorkbenchArt.DIM;
            WorkbenchArt.left(graphics, draft().choiceName(option, index), x + 5, rowY + (ROW - 8) / 2, textWidth, colour);
        }
        if (scrolls) {
            int trackY = y + 2;
            int track = rows() * ROW;
            int thumb = Math.max(6, track * rows() / draft().choices(option));
            int thumbY = trackY + (track - thumb) * scroll / maxScroll();
            int barX = x + width - 3 - BAR;
            graphics.fill(barX, trackY, barX + BAR, trackY + track, WorkbenchArt.WELL);
            graphics.fill(barX, thumbY, barX + BAR, thumbY + thumb, WorkbenchArt.DIM);
        }
        graphics.pose().popPose();
    }
}
