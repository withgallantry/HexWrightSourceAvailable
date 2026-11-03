package com.bluup.hexwright.client.hexweave;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberColor;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@LDLRegister(name = "loom_button", group = LoomCanvasWidget.EDITOR_GROUP)
public class LoomButtonWidget extends ButtonWidget {

    @Configurable(name = "gui.hexwright.editor.name.loom_action", tips = "gui.hexwright.editor.name.loom_action.tips")
    private LoomAction action = LoomAction.SAVE;
    @Configurable(name = "gui.hexwright.editor.name.show_label")
    private boolean showLabel = true;
    @Configurable(name = "gui.hexwright.editor.name.label_colour")
    @NumberColor
    private int labelColour = 0xFFFFFFFF;
    @Configurable(name = "gui.hexwright.editor.name.disabled_label_colour")
    @NumberColor
    private int disabledLabelColour = 0xFFA0A0A0;
    @Configurable(name = "gui.hexwright.editor.name.label_shadow")
    private boolean labelShadow = true;
    @Configurable(name = "gui.hexwright.editor.name.disabled_texture")
    private IGuiTexture disabledTexture = vanillaButton(46);
    @Configurable(name = "gui.hexwright.editor.name.armed_texture", tips = "gui.hexwright.editor.name.armed_texture.tips")
    private IGuiTexture armedTexture;

    @Environment(EnvType.CLIENT)
    private LoomEditor editor;

    public LoomButtonWidget() {
        super(0, 0, 84, 20, null);
        setBackground(vanillaButton(66));
        setHoverTexture(vanillaButton(86));
    }

    private static IGuiTexture vanillaButton(int row) {
        return new ResourceBorderTexture("minecraft:textures/gui/widgets.png", 200, 20, 3, 3)
            .setSliceMode(ResourceBorderTexture.NineSliceMode.TILE)
            .setImageWidthHeight(200 / 256f, 20 / 256f)
            .setImageOffset(0, row / 256f);
    }

    static LoomButtonWidget of(LoomAction action, int x, int y, int width, int height) {
        LoomButtonWidget button = new LoomButtonWidget();
        button.action = action;
        button.setSelfPosition(x, y);
        button.setSize(width, height);
        return button;
    }

    @Override
    public void initTemplate() {
    }

    LoomAction action() {
        return action;
    }

    @Environment(EnvType.CLIENT)
    void bind(LoomEditor editor) {
        this.editor = editor;
    }

    @Environment(EnvType.CLIENT)
    private boolean enabled() {
        return editor == null || editor.active(action);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOverElement(mouseX, mouseY)) {
            return false;
        }
        if (editor != null && editor.active(action)) {
            isClicked = true;
            playButtonClickSound();
            editor.press(action);
        }
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Position position = getPosition();
        Size size = getSize();
        boolean enabled = enabled();
        boolean hovered = isMouseOverElement(mouseX, mouseY);
        IGuiTexture face = !enabled && disabledTexture != null ? disabledTexture
            : editor != null && editor.armed(action) && armedTexture != null ? armedTexture
            : isClicked && clickedTexture != null ? clickedTexture
            : backgroundTexture;
        if (face != null && (!enabled || !hovered || drawBackgroundWhenHover)) {
            face.draw(graphics, mouseX, mouseY, position.x, position.y, size.width, size.height);
        }
        if (enabled && hovered && hoverTexture != null) {
            hoverTexture.draw(graphics, mouseX, mouseY, position.x, position.y, size.width, size.height);
        }
        if (showLabel) {
            drawLabel(graphics, enabled ? labelColour : disabledLabelColour);
        }
    }

    @Environment(EnvType.CLIENT)
    private void drawLabel(GuiGraphics graphics, int colour) {
        Font font = Minecraft.getInstance().font;
        Component label = editor != null ? editor.label(action) : action.label();
        Position position = getPosition();
        Size size = getSize();
        FormattedCharSequence text = font.split(label, Math.max(1, size.width - 4)).stream().findFirst()
            .orElse(FormattedCharSequence.EMPTY);
        int x = position.x + (size.width - font.width(text)) / 2;
        int y = position.y + (size.height - 8) / 2;
        graphics.drawString(font, text, x, y, colour, labelShadow);
    }
}
