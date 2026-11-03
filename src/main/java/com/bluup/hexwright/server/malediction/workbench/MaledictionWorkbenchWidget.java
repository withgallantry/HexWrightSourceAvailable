package com.bluup.hexwright.server.malediction.workbench;

import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class MaledictionWorkbenchWidget extends Widget implements IConfigurableWidget {

    public static final String EDITOR_GROUP = "widget.malediction_workbench";

    private @Nullable MaledictionSession session;

    protected MaledictionWorkbenchWidget(int width, int height) {
        super(0, 0, width, height);
    }

    void bind(MaledictionSession session) {
        this.session = session;
    }

    protected @Nullable MaledictionSession session() {
        return session;
    }

    @Environment(EnvType.CLIENT)
    protected void showTooltip(List<Component> lines) {
        if (gui != null && gui.getModularUIGui() != null) {
            gui.getModularUIGui().setHoverTooltip(lines, ItemStack.EMPTY, null, null);
        }
    }
}
