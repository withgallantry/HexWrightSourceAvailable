package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.client.flourish.FlourishWorkbenchOptions;
import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.common.flourish.FlourishMotif;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

public abstract class WorkbenchWidget extends Widget implements IConfigurableWidget {

    public static final String EDITOR_GROUP = "widget.flourish_workbench";

    private @Nullable WorkbenchSession session;

    protected WorkbenchWidget(int width, int height) {
        super(0, 0, width, height);
    }

    void bind(WorkbenchSession session) {
        this.session = session;
    }

    protected @Nullable WorkbenchSession session() {
        return session;
    }

    protected FlourishDraft draft() {
        return session != null ? session.draft() : Sample.DRAFT;
    }

    protected boolean known(@Nullable Flourish flourish) {
        return flourish == null || session == null || session.known(flourish);
    }

    @Environment(EnvType.CLIENT)
    protected void showTooltip(List<Component> lines) {
        if (FlourishWorkbenchOptions.tooltips() && gui != null && gui.getModularUIGui() != null) {
            gui.getModularUIGui().setHoverTooltip(lines, ItemStack.EMPTY, null, null);
        }
    }

    @Environment(EnvType.CLIENT)
    protected static boolean untransformed(GuiGraphics graphics) {
        Matrix4f pose = graphics.pose().last().pose();
        return pose.m00() == 1f && pose.m11() == 1f && pose.m30() == 0f && pose.m31() == 0f;
    }

    private static final class Sample {
        static final FlourishDraft DRAFT = create();

        private static FlourishDraft create() {
            FlourishDraft draft = new FlourishDraft();
            draft.load(new FlourishDesign(List.of(
                FlourishDesign.Layer.of(FlourishMotif.FIGURE),
                new FlourishDesign.Layer(FlourishMotif.STAR, FlourishMotif.Placement.CENTRE, 1, 0, 1),
                new FlourishDesign.Layer(FlourishMotif.BEAM, FlourishMotif.Placement.RISING, 2, 3, 2)),
                Flourish.HEXAGRAM, FlourishDesign.Motes.RING));
            return draft;
        }
    }
}
