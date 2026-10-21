package com.bluup.hexwright.server.research;

import com.bluup.hexwright.client.inspiration.ClientInspiration;
import com.bluup.hexwright.client.inspiration.InspirationHud;
import com.bluup.hexwright.server.inspiration.InspirationLevels;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@LDLRegister(name = "inspiration_bar", group = ResearchWidget.EDITOR_GROUP)
public class InspirationBarWidget extends Widget implements IConfigurableWidget {

    public InspirationBarWidget() {
        super(0, 0, InspirationHud.BAR_WIDTH, InspirationHud.LEVEL_RAISE + InspirationHud.BAR_HEIGHT);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        InspirationHud.drawBar(graphics, Minecraft.getInstance().font,
            getPositionX() + (getSizeWidth() - InspirationHud.BAR_WIDTH) / 2,
            getPositionY() + getSizeHeight() - InspirationHud.BAR_HEIGHT, 1.0F);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (!isMouseOverElement(mouseX, mouseY) || gui == null || gui.getModularUIGui() == null) {
            return;
        }
        gui.getModularUIGui().setHoverTooltip(List.of(Component.translatable("gui.hexwright.research.inspiration",
                ClientInspiration.levels(), ClientInspiration.total() % InspirationLevels.POINTS_PER_LEVEL,
                InspirationLevels.POINTS_PER_LEVEL)),
            ItemStack.EMPTY, null, null);
    }
}
