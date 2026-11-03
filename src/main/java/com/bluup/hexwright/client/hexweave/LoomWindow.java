package com.bluup.hexwright.client.hexweave;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.HexwrightDev;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public final class LoomWindow implements IUIHolder {

    static final String TEMPLATE = "loom";

    private final LoomEditor editor;

    private LoomWindow(LoomEditor editor) {
        this.editor = editor;
    }

    static void open(LoomEditor editor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        WidgetGroup root = layout();
        bind(root, editor);
        LoomFrame frame = new LoomFrame(root);
        frame.setClientSideWidget();
        ModularUI ui = new ModularUI(new LoomWindow(editor), minecraft.player).widget(frame);
        ui.initWidgets();
        ModularUIGuiContainer screen = new ModularUIGuiContainer(ui, minecraft.player.containerMenu.containerId);
        editor.setCloser(() -> {
            if (minecraft.screen == screen) {
                minecraft.setScreen(null);
            }
        });
        minecraft.setScreen(screen);
    }

    static @Nullable LoomEditor current() {
        return Minecraft.getInstance().screen instanceof ModularUIGuiContainer screen
            && screen.modularUI.holder instanceof LoomWindow window ? window.editor : null;
    }

    private static WidgetGroup layout() {
        Supplier<WidgetGroup> template = UiTemplates.loadOptional(TEMPLATE);
        if (template == null) {
            HexwrightDebug.log(HexwrightDebug.WEAVE, "[loom] no {}.ui; using the built-in layout", TEMPLATE);
            return LoomLayout.build();
        }
        WidgetGroup root = template.get();
        if (root == null || root.getWidgetsByType(LoomCanvasWidget.class).isEmpty()) {
            Hexwright.LOGGER.warn("{}.ui has no loom_canvas to weave in; using the built-in Loom layout", TEMPLATE);
            return LoomLayout.build();
        }
        return root;
    }

    private static void bind(WidgetGroup root, LoomEditor editor) {
        List<LoomCanvasWidget> canvases = root.getWidgetsByType(LoomCanvasWidget.class);
        if (canvases.size() > 1) {
            Hexwright.LOGGER.warn("{}.ui has {} loom_canvas widgets; only the first is woven in", TEMPLATE, canvases.size());
        }
        canvases.get(0).bind(editor);
        List<LoomButtonWidget> buttons = root.getWidgetsByType(LoomButtonWidget.class);
        for (LoomButtonWidget button : buttons) {
            button.bind(editor);
        }
        for (LoomLabelWidget label : root.getWidgetsByType(LoomLabelWidget.class)) {
            label.bind(editor);
        }
        for (LoomModeGroupWidget group : root.getWidgetsByType(LoomModeGroupWidget.class)) {
            group.bind(editor);
        }
        root.addWidget(new Controls(editor, buttons));
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return null;
    }

    @Override
    public boolean isInvalid() {
        return false;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public void markAsDirty() {
    }

    private static final class Controls extends Widget {

        private final LoomEditor editor;
        private final List<LoomButtonWidget> buttons;

        Controls(LoomEditor editor, List<LoomButtonWidget> buttons) {
            super(0, 0, 0, 0);
            this.editor = editor;
            this.buttons = buttons;
        }

        @Override
        public void updateScreen() {
            super.updateScreen();
            editor.tick();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (editor.transitioning()) {
                return true;
            }
            if (editor.capturing()) {
                editor.captureMouse(button);
                return true;
            }
            LoomAction armed = editor.armed();
            if (armed != null && buttons.stream().noneMatch(widget -> widget.action() == armed
                && shown(widget) && widget.isMouseOverElement(mouseX, mouseY))) {
                editor.disarm();
            }
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_F5 && HexwrightDev.LOOM_TOOLS) {
                open(editor);
                editor.setStatus(Component.translatable("gui.hexwright.hex_loom.dev.reloaded"));
                return true;
            }
            if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode) && !editor.capturing()) {
                return true;
            }
            return editor.keyPressed(keyCode, scanCode, modifiers);
        }

        private static boolean shown(Widget widget) {
            for (Widget at = widget; at != null; at = at.getParent()) {
                if (!at.isVisible()) {
                    return false;
                }
            }
            return true;
        }
    }
}
