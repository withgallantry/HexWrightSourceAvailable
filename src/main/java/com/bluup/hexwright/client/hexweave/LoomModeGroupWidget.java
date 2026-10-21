package com.bluup.hexwright.client.hexweave;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

@LDLRegister(name = "loom_mode_group", group = LoomCanvasWidget.EDITOR_GROUP)
public class LoomModeGroupWidget extends WidgetGroup {

    public enum Mode {
        WEAVE,
        THREAD,
        BOTH
    }

    @Configurable(name = "gui.hexwright.editor.name.loom_mode", tips = "gui.hexwright.editor.name.loom_mode.tips")
    private Mode mode = Mode.WEAVE;

    @Environment(EnvType.CLIENT)
    private LoomEditor editor;

    public LoomModeGroupWidget() {
        super(0, 0, 84, 148);
    }

    static LoomModeGroupWidget of(Mode mode, int x, int y, int width, int height) {
        LoomModeGroupWidget group = new LoomModeGroupWidget();
        group.mode = mode;
        group.setSelfPosition(x, y);
        group.setSize(width, height);
        return group;
    }

    @Override
    public void initTemplate() {
        setBackground(new ColorRectTexture(0x20FFFFFF));
    }

    @Environment(EnvType.CLIENT)
    void bind(LoomEditor editor) {
        this.editor = editor;
        follow();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        follow();
    }

    @Environment(EnvType.CLIENT)
    private void follow() {
        if (mode == Mode.BOTH) {
            setVisible(true);
        } else if (editor != null) {
            setVisible(editor.inThread() == (mode == Mode.THREAD));
        } else {
            LoomCanvasWidget canvas = nearestCanvas();
            setVisible(canvas == null || (canvas.preview() == LoomCanvasWidget.Preview.THREAD) == (mode == Mode.THREAD));
        }
    }

    @Environment(EnvType.CLIENT)
    private LoomCanvasWidget nearestCanvas() {
        for (WidgetGroup group = getParent(); group != null; group = group.getParent()) {
            List<LoomCanvasWidget> canvases = group.getWidgetsByType(LoomCanvasWidget.class);
            if (!canvases.isEmpty()) {
                return canvases.get(0);
            }
        }
        return null;
    }
}
