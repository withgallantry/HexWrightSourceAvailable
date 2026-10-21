package com.bluup.hexwright.client.hexweave;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberColor;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;


@LDLRegister(name = "loom_label", group = LoomCanvasWidget.EDITOR_GROUP)
public class LoomLabelWidget extends Widget implements IConfigurableWidget {

    public enum Kind {
        SUMMARY,
        STATUS
    }

    public enum TextAlign {
        LEFT,
        CENTRE,
        RIGHT
    }

    @Configurable(name = "gui.hexwright.editor.name.loom_text")
    private Kind kind = Kind.STATUS;
    @Configurable(name = "gui.hexwright.editor.name.text_align")
    private TextAlign textAlign = TextAlign.LEFT;
    @Configurable(name = "gui.hexwright.editor.name.label_colour")
    @NumberColor
    private int colour = 0xFFFFFFFF;
    @Configurable(name = "gui.hexwright.editor.name.quiet_colour", tips = "gui.hexwright.editor.name.quiet_colour.tips")
    @NumberColor
    private int quietColour = 0xFF8C80A8;
    @Configurable(name = "gui.hexwright.editor.name.label_shadow")
    private boolean shadow = true;

    @Environment(EnvType.CLIENT)
    private LoomEditor editor;

    public LoomLabelWidget() {
        super(0, 0, 200, 10);
    }

    static LoomLabelWidget of(Kind kind, TextAlign align, int colour, int x, int y, int width, int height) {
        LoomLabelWidget label = new LoomLabelWidget();
        label.kind = kind;
        label.textAlign = align;
        label.colour = colour;
        label.setSelfPosition(x, y);
        label.setSize(width, height);
        return label;
    }

    @Environment(EnvType.CLIENT)
    void bind(LoomEditor editor) {
        this.editor = editor;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        Component text;
        boolean quiet = false;
        if (kind == Kind.SUMMARY) {
            text = editor != null ? editor.summary()
                : Component.translatable("gui.hexwright.hex_loom.weave", 3, 2);
        } else if (editor != null) {
            LoomEditor.StatusLine line = editor.statusLine();
            text = line.text();
            quiet = line.quiet();
        } else {
            text = Component.translatable("gui.hexwright.hex_loom.help_weave");
            quiet = true;
        }
        Font font = Minecraft.getInstance().font;
        Position position = getPosition();
        Size size = getSize();
        FormattedCharSequence shown = font.split(text, Math.max(1, size.width)).stream().findFirst()
            .orElse(FormattedCharSequence.EMPTY);
        int free = size.width - font.width(shown);
        int x = position.x + switch (textAlign) {
            case LEFT -> 0;
            case CENTRE -> free / 2;
            case RIGHT -> free;
        };
        int y = position.y + (size.height - 8) / 2;
        graphics.drawString(font, shown, x, y, quiet ? quietColour : colour, shadow);
    }
}
