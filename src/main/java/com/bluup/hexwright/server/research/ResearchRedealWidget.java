package com.bluup.hexwright.server.research;

import com.bluup.hexwright.Hexwright;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@LDLRegister(name = "research_redeal", group = ResearchWidget.EDITOR_GROUP)
public class ResearchRedealWidget extends ResearchWidget {

    private static final int ACTION_REDEAL = 1;
    private static final ResourceLocation ICON = Hexwright.id("textures/gui/research/redeal.png");
    private static final int ICON_SIZE = 12;

    public ResearchRedealWidget() {
        super(18, 18);
    }

    private boolean usable() {
        return session().isRunning();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && usable() && isMouseOverElement(mouseX, mouseY)) {
            writeClientAction(ACTION_REDEAL, buf -> {
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
        if (id == ACTION_REDEAL && player != null && table != null) {
            table.redeal(player);
        } else {
            super.handleClientAction(id, buffer);
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (!session().hasTablet()) {
            return;
        }
        int x = getPositionX();
        int y = getPositionY();
        boolean usable = usable();
        boolean lit = usable && ResearchTrayWidget.carried(gui) == null && isMouseOverElement(mouseX, mouseY);
        (lit ? ResearchArt.BOX_LIT : ResearchArt.BOX).draw(graphics, mouseX, mouseY, x, y, getSizeWidth(), getSizeHeight());
        ResearchArt.tint(graphics, usable ? ResearchArt.GOLD : ResearchArt.DIM, 1f);
        graphics.blit(ICON, x + (getSizeWidth() - ICON_SIZE) / 2, y + (getSizeHeight() - ICON_SIZE) / 2,
            0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (!usable() || !isMouseOverElement(mouseX, mouseY) || gui == null || gui.getModularUIGui() == null) {
            return;
        }
        gui.getModularUIGui().setHoverTooltip(List.of(
                Component.translatable("gui.hexwright.research.redeal"),
                Component.translatable("gui.hexwright.research.redeal.hint").withStyle(ChatFormatting.GRAY)),
            ItemStack.EMPTY, null, null);
    }
}
