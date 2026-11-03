package com.bluup.hexwright.server.flourish.workbench;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import org.jetbrains.annotations.Nullable;

@LDLRegister(name = "flourish_page", group = WorkbenchWidget.EDITOR_GROUP)
public class WorkbenchPageGroup extends WidgetGroup {

    @Configurable(name = "gui.hexwright.editor.name.flourish_page", tips = "gui.hexwright.editor.name.flourish_page.tips")
    private WorkbenchPage page = WorkbenchPage.LAYERS;

    private @Nullable WorkbenchSession session;

    public WorkbenchPageGroup() {
        super(0, 0, 182, 78);
    }

    static WorkbenchPageGroup of(WorkbenchPage page, int x, int y, int width, int height) {
        WorkbenchPageGroup group = new WorkbenchPageGroup();
        group.page = page;
        group.setSelfPosition(x, y);
        group.setSize(width, height);
        return group;
    }

    void bind(WorkbenchSession session) {
        this.session = session;
    }

    @Override
    public boolean isVisible() {
        return super.isVisible() && (session == null || session.draft().page() == page);
    }
}
