package com.bluup.hexwright.server.flourish.workbench;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class WorkbenchFieldWidget extends WorkbenchWidget {

    protected static final int HEADER_H = 11;

    @Configurable(name = "gui.hexwright.editor.name.flourish_option", tips = "gui.hexwright.editor.name.flourish_option.tips")
    protected WorkbenchOption option;

    protected WorkbenchFieldWidget(WorkbenchOption option, int width, int height) {
        super(width, height);
        this.option = option;
    }

    protected boolean applies() {
        return draft().applies(option);
    }

    protected int controlY() {
        return getPositionY() + HEADER_H;
    }

    protected int controlHeight() {
        return getSizeHeight() - HEADER_H;
    }

    protected @Nullable Component headerValue() {
        return null;
    }

    @Environment(EnvType.CLIENT)
    protected abstract void drawControl(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height);

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int width = getSizeWidth();
        boolean applies = applies();
        Component value = headerValue();
        int valueWidth = value == null ? 0 : width / 2;
        WorkbenchArt.left(graphics, option.label(), x, getPositionY(), width - valueWidth,
            applies ? WorkbenchArt.PALE : WorkbenchArt.DIM);
        if (value != null) {
            WorkbenchArt.right(graphics, value, x + width - valueWidth, getPositionY(), valueWidth,
                applies ? WorkbenchArt.GOLD : WorkbenchArt.DIM);
        }
        drawControl(graphics, mouseX, mouseY, x, controlY(), width, controlHeight());
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (option.hasTooltip() && isMouseOverElement(mouseX, mouseY)) {
            showTooltip(List.of(
                option.label().copy().withStyle(ChatFormatting.GOLD),
                option.hint().copy().withStyle(ChatFormatting.GRAY)));
        }
    }
}
