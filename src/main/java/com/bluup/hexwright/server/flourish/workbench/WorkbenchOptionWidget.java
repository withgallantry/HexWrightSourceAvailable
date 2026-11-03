package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishPreview;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

@LDLRegister(name = "flourish_option", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchOptionWidget extends WorkbenchWidget {

    private static final int ARROW = 10;

    @Configurable(name = "gui.hexwright.editor.name.flourish_option", tips = "gui.hexwright.editor.name.flourish_option.tips")
    private WorkbenchOption option = WorkbenchOption.PLACEMENT;
    @Configurable(name = "gui.hexwright.editor.name.flourish_option_face")
    private IGuiTexture faceTexture = WorkbenchArt.box();
    @Configurable(name = "gui.hexwright.editor.name.flourish_option_hover")
    private IGuiTexture hoverFaceTexture = WorkbenchArt.boxLit();

    public WorkbenchOptionWidget() {
        super(124, 18);
    }

    static WorkbenchOptionWidget of(WorkbenchOption option) {
        WorkbenchOptionWidget widget = new WorkbenchOptionWidget();
        widget.option = option;
        return widget;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOverElement(mouseX, mouseY) || button > 1) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        FlourishDraft draft = draft();
        if (!draft.applies(option)) {
            return true;
        }
        boolean back = button == 1 || mouseX < getPositionX() + ARROW + 2;
        draft.cycle(option, back ? -1 : 1);
        if (option == WorkbenchOption.CHIME) {
            FlourishPreview.chime(draft.chime());
        } else {
            playButtonClickSound();
        }
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
        boolean hovered = applies && isMouseOverElement(mouseX, mouseY);
        (hovered ? hoverFaceTexture : faceTexture).draw(graphics, mouseX, mouseY, x, y, width, height);

        Font font = Minecraft.getInstance().font;
        int textY = y + (height - 8) / 2;
        boolean backHovered = hovered && mouseX < x + ARROW + 2;
        graphics.drawString(font, "‹", x + 4, textY, backHovered ? WorkbenchArt.GOLD : WorkbenchArt.DIM, false);
        graphics.drawString(font, "›", x + width - 4 - font.width("›"), textY,
            hovered && !backHovered ? WorkbenchArt.GOLD : WorkbenchArt.DIM, false);

        boolean refused = option == WorkbenchOption.CHIME && !known(draft().chime());
        int valueColour = !applies ? WorkbenchArt.DIM : refused ? WorkbenchArt.REFUSED : WorkbenchArt.GOLD;
        int room = width - ARROW * 2;
        Component value = draft().value(option);
        Component labelled = option.label().copy().withStyle(style -> style.withColor(WorkbenchArt.DIM & 0xFFFFFF))
            .append(Component.literal(" ").append(value).withStyle(style -> style.withColor(valueColour & 0xFFFFFF)));
        WorkbenchArt.centred(graphics, WorkbenchArt.fits(labelled, room) ? labelled : value, x + ARROW, textY, room,
            valueColour);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (!option.hasTooltip() || !isMouseOverElement(mouseX, mouseY)) {
            return;
        }
        showTooltip(List.of(
            option.label().copy().withStyle(ChatFormatting.GOLD),
            option.hint().copy().withStyle(ChatFormatting.GRAY)));
    }
}
