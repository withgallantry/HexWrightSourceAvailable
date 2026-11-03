package com.bluup.hexwright.server.malediction.workbench;

import com.bluup.hexwright.server.flourish.workbench.WorkbenchArt;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@LDLRegister(name = "malediction_fuse", group = MaledictionWorkbenchWidget.EDITOR_GROUP)
public class FuseWidget extends MaledictionWorkbenchWidget {

    @Configurable(name = "gui.hexwright.editor.name.flourish_action_face")
    private IGuiTexture faceTexture = WorkbenchArt.box();
    @Configurable(name = "gui.hexwright.editor.name.flourish_action_hover")
    private IGuiTexture hoverFaceTexture = WorkbenchArt.boxLit();

    public FuseWidget() {
        super(82, 20);
    }

    private boolean enabled() {
        MaledictionSession session = session();
        return session == null || session.canFuse();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOverElement(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        MaledictionSession session = session();
        if (enabled() && session != null) {
            playButtonClickSound();
            session.fuse();
        }
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX();
        int y = getPositionY();
        boolean enabled = enabled();
        boolean lit = enabled && isMouseOverElement(mouseX, mouseY);
        (lit ? hoverFaceTexture : faceTexture).draw(graphics, mouseX, mouseY, x, y, getSizeWidth(), getSizeHeight());
        WorkbenchArt.centred(graphics, Component.translatable("gui.hexwright.malediction_workbench.fuse"), x + 2,
            y + (getSizeHeight() - 8) / 2, getSizeWidth() - 4, enabled ? WorkbenchArt.GOLD : WorkbenchArt.DIM);
    }
}
