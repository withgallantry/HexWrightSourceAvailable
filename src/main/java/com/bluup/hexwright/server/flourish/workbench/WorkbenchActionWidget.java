package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.common.flourish.Flourish;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@LDLRegister(name = "flourish_action", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchActionWidget extends WorkbenchWidget {

    @Configurable(name = "gui.hexwright.editor.name.flourish_action", tips = "gui.hexwright.editor.name.flourish_action.tips")
    private WorkbenchAction action = WorkbenchAction.INSCRIBE;
    @Configurable(name = "gui.hexwright.editor.name.flourish_action_face")
    private IGuiTexture faceTexture = WorkbenchArt.box();
    @Configurable(name = "gui.hexwright.editor.name.flourish_action_hover")
    private IGuiTexture hoverFaceTexture = WorkbenchArt.boxLit();
    @Configurable(name = "gui.hexwright.editor.name.flourish_action_link", tips = "gui.hexwright.editor.name.flourish_action_link.tips")
    private boolean link;

    public WorkbenchActionWidget() {
        super(82, 18);
    }

    static WorkbenchActionWidget of(WorkbenchAction action) {
        WorkbenchActionWidget widget = new WorkbenchActionWidget();
        widget.action = action;
        return widget;
    }

    static WorkbenchActionWidget link(WorkbenchAction action) {
        WorkbenchActionWidget widget = of(action);
        widget.link = true;
        return widget;
    }

    private boolean enabled() {
        WorkbenchSession session = session();
        return switch (action) {
            case INSCRIBE -> session == null || session.canInscribe();
            case REMOVE_LAYER -> draft().canRemove();
            case REMOVE_RING -> draft().canRemoveRing();
            case RESET, BACK -> true;
        };
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOverElement(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (enabled()) {
            playButtonClickSound();
            FlourishDraft draft = draft();
            switch (action) {
                case INSCRIBE -> {
                    WorkbenchSession session = session();
                    if (session != null) {
                        session.inscribe();
                    }
                }
                case REMOVE_LAYER -> draft.remove();
                case REMOVE_RING -> draft.removeRing();
                case RESET -> draft.reset();
                case BACK -> draft.page(WorkbenchPage.LAYERS);
            }
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
        if (link) {
            WorkbenchArt.right(graphics, action.label(), x, y + (getSizeHeight() - 8) / 2, getSizeWidth(),
                !enabled ? WorkbenchArt.DIM : lit ? WorkbenchArt.GOLD : WorkbenchArt.PALE);
            return;
        }
        (lit ? hoverFaceTexture : faceTexture).draw(graphics, mouseX, mouseY, x, y, getSizeWidth(), getSizeHeight());
        WorkbenchArt.centred(graphics, action.label(), x + 2, y + (getSizeHeight() - 8) / 2, getSizeWidth() - 4,
            enabled ? WorkbenchArt.GOLD : WorkbenchArt.DIM);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (action != WorkbenchAction.INSCRIBE || !isMouseOverElement(mouseX, mouseY)) {
            return;
        }
        Component why = why();
        if (why != null) {
            showTooltip(List.of(action.label().copy().withStyle(ChatFormatting.GOLD),
                why.copy().withStyle(ChatFormatting.GRAY)));
        }
    }

    private @Nullable Component why() {
        WorkbenchSession session = session();
        if (session == null || session.canInscribe()) {
            return null;
        }
        if (!session.workbench().hasBlank()) {
            return Component.translatable("gui.hexwright.flourish_workbench.status.no_blank");
        }
        Flourish missing = session.missing();
        if (missing != null) {
            return Component.translatable("gui.hexwright.flourish_workbench.motif.locked",
                Component.translatable("item.hexwright." + missing.itemId()));
        }
        return Component.translatable("gui.hexwright.flourish_workbench.status.inscribed");
    }
}
