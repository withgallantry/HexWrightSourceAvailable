package com.bluup.hexwright.client.hexweave;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberColor;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

@LDLRegister(name = "loom_canvas", group = LoomCanvasWidget.EDITOR_GROUP)
public class LoomCanvasWidget extends Widget implements IConfigurableWidget {

    public static final String EDITOR_GROUP = "widget.hex_loom";

    public enum Preview {
        WEAVE,
        THREAD
    }

    @Configurable(name = "gui.hexwright.editor.name.loom_preview", tips = "gui.hexwright.editor.name.loom_preview.tips")
    private Preview preview = Preview.WEAVE;
    @Configurable(name = "gui.hexwright.editor.name.loom_eye")
    @NumberColor
    private int eyeColour = 0xFF000000 | LoomPalette.DEFAULT.eye();
    @Configurable(name = "gui.hexwright.editor.name.loom_tail")
    @NumberColor
    private int tailColour = 0xFF000000 | LoomPalette.DEFAULT.tail();
    @Configurable(name = "gui.hexwright.editor.name.loom_trigger")
    @NumberColor
    private int triggerColour = 0xFF000000 | LoomPalette.DEFAULT.trigger();
    @Configurable(name = "gui.hexwright.editor.name.loom_selected")
    @NumberColor
    private int selectedColour = 0xFF000000 | LoomPalette.DEFAULT.selected();
    @Configurable(name = "gui.hexwright.editor.name.loom_selected_fade")
    @NumberColor
    private int selectedFadeColour = 0xFF000000 | LoomPalette.DEFAULT.selectedFade();
    @Configurable(name = "gui.hexwright.editor.name.loom_grid")
    @NumberColor
    private int gridColour = 0xFF000000 | LoomPalette.DEFAULT.grid();
    @Configurable(name = "gui.hexwright.editor.name.loom_rim")
    @NumberColor
    private int rimColour = 0xFF000000 | LoomPalette.DEFAULT.rim();
    @Configurable(name = "gui.hexwright.editor.name.loom_label")
    @NumberColor
    private int labelColour = 0xFF000000 | LoomPalette.DEFAULT.label();
    @Configurable(name = "gui.hexwright.editor.name.loom_number")
    @NumberColor
    private int numberColour = 0xFF000000 | LoomPalette.DEFAULT.number();
    @Configurable(name = "gui.hexwright.editor.name.loom_insert")
    @NumberColor
    private int insertColour = 0xFF000000 | LoomPalette.DEFAULT.insert();
    @Configurable(name = "gui.hexwright.editor.name.loom_timeline_height", tips = "gui.hexwright.editor.name.loom_timeline_height.tips")
    @NumberRange(range = {16, 96})
    private int timelineHeight = 32;
    @Configurable(name = "gui.hexwright.editor.name.loom_timeline_background")
    private IGuiTexture timelineBackground = panel("internal-heavy");
    @Configurable(name = "gui.hexwright.editor.name.loom_timeline_cell")
    private IGuiTexture timelineCell = panel("section-meium");
    @Configurable(name = "gui.hexwright.editor.name.loom_timeline_cell_hover")
    private IGuiTexture timelineCellHover = panel("input-default");
    @Configurable(name = "gui.hexwright.editor.name.loom_timeline_cell_selected", tips = "gui.hexwright.editor.name.loom_timeline_cell_selected.tips")
    private IGuiTexture timelineCellSelected;
    @Configurable(name = "gui.hexwright.editor.name.loom_menu_panel", tips = "gui.hexwright.editor.name.loom_menu.tips")
    private IGuiTexture menuPanel = panel("main-border");
    @Configurable(name = "gui.hexwright.editor.name.loom_menu_title", tips = "gui.hexwright.editor.name.loom_menu.tips")
    private IGuiTexture menuTitle = panel("section-header");
    @Configurable(name = "gui.hexwright.editor.name.loom_menu_well", tips = "gui.hexwright.editor.name.loom_menu.tips")
    private IGuiTexture menuWell = panel("internal-medium");
    @Configurable(name = "gui.hexwright.editor.name.loom_menu_row", tips = "gui.hexwright.editor.name.loom_menu.tips")
    private IGuiTexture menuRow = panel("menu/list-box");
    @Configurable(name = "gui.hexwright.editor.name.loom_menu_row_hover", tips = "gui.hexwright.editor.name.loom_menu.tips")
    private IGuiTexture menuRowHover = panel("menu/list-box-select");

    @Environment(EnvType.CLIENT)
    private LoomEditor editor;
    @Environment(EnvType.CLIENT)
    private LoomEditor previewEditor;
    private Preview previewShown;

    public LoomCanvasWidget() {
        super(0, 0, 296, 204);
        setBackground(new ColorRectTexture(0x40100A20));
    }

    private static IGuiTexture panel(String name) {
        return new ResourceBorderTexture("ldlib:textures/" + name + ".png", 180, 180, 4, 4);
    }

    @Environment(EnvType.CLIENT)
    void bind(LoomEditor editor) {
        this.editor = editor;
    }

    public Preview preview() {
        return preview;
    }

    @Environment(EnvType.CLIENT)
    private LoomEditor shown() {
        if (editor != null) {
            return editor;
        }
        if (previewEditor == null || previewShown != preview) {
            previewEditor = LoomEditor.preview(preview == Preview.THREAD);
            previewShown = preview;
        }
        return previewEditor;
    }

    @Environment(EnvType.CLIENT)
    private LoomPalette palette() {
        return LoomPalette.of(eyeColour, tailColour, triggerColour, selectedColour, selectedFadeColour,
            gridColour, rimColour, labelColour, numberColour, insertColour);
    }

    @Environment(EnvType.CLIENT)
    private ThreadTimeline.Look timelineLook() {
        return new ThreadTimeline.Look(timelineBackground, timelineCell, timelineCellHover, timelineCellSelected);
    }

    @Environment(EnvType.CLIENT)
    private LoomMenu.Look menuLook() {
        return new LoomMenu.Look(menuPanel, menuTitle, menuWell, menuRow, menuRowHover);
    }

    @Environment(EnvType.CLIENT)
    private static boolean untransformed(GuiGraphics graphics) {
        Matrix4f pose = graphics.pose().last().pose();
        return pose.m00() == 1f && pose.m11() == 1f && pose.m30() == 0f && pose.m31() == 0f;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        Position position = getPosition();
        Size size = getSize();
        if (!untransformed(graphics)) {
            float unit = Math.min(size.width / (2 * ThreadFrame.HALF_WIDTH + 2), size.height / (2 * ThreadFrame.HALF_HEIGHT + 2));
            ThreadFrame.draw(graphics, position.x + size.width / 2f, position.y + size.height / 2f, unit, unit, 1f);
            return;
        }
        LoomEditor shown = shown();
        shown.setArea(position.x, position.y, position.x + size.width, position.y + size.height, timelineHeight);
        RenderSystem.depthMask(true);
        shown.render(graphics, mouseX, mouseY, palette(), timelineLook(), menuLook());
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (editor == null || gui == null || gui.getModularUIGui() == null
            || gui.mainGroup.getHoverElement(mouseX, mouseY) != this) {
            return;
        }
        List<Component> hover = editor.hover(mouseX, mouseY);
        if (hover != null) {
            gui.getModularUIGui().setHoverTooltip(hover, ItemStack.EMPTY, null, null);
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return editor != null && editor.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return editor != null && editor.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return editor != null && editor.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        return editor != null && editor.mouseScrolled(mouseX, mouseY, wheelDelta);
    }
}
