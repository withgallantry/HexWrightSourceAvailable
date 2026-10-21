package com.bluup.hexwright.server.research;

import com.bluup.hexwright.server.item.ArtifactItem;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@LDLRegister(name = "research_use", group = ResearchWidget.EDITOR_GROUP)
public class ResearchUseWidget extends ResearchWidget {

    private static final int ACTION_USE = 1;

    public ResearchUseWidget() {
        super(86, 18);
    }

    private boolean usable() {
        return session().canSelect();
    }

    @Override
    public boolean isVisible() {
        return super.isVisible() && (table() == null || !session().isRunning());
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && usable() && isMouseOverElement(mouseX, mouseY)) {
            writeClientAction(ACTION_USE, buf -> {
            });
            playButtonClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        ServerPlayer player = sender();
        ResearchTableBlockEntity table = table();
        if (id != ACTION_USE || player == null || table == null) {
            super.handleClientAction(id, buffer);
            return;
        }
        Component refusal = table.use(player);
        if (refusal != null) {
            player.displayClientMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int y = getPositionY();
        boolean usable = usable();
        boolean lit = usable && ResearchTrayWidget.carried(gui) == null && isMouseOverElement(mouseX, mouseY);
        (lit ? ResearchArt.BOX_LIT : ResearchArt.BOX).draw(graphics, mouseX, mouseY, x, y, getSizeWidth(), getSizeHeight());
        graphics.drawCenteredString(Minecraft.getInstance().font, Component.translatable("gui.hexwright.research.deal"),
            x + getSizeWidth() / 2, y + (getSizeHeight() - 8) / 2, usable ? ResearchArt.GOLD : ResearchArt.DIM);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (!usable() || !isMouseOverElement(mouseX, mouseY) || gui == null || gui.getModularUIGui() == null) {
            return;
        }
    }
}
