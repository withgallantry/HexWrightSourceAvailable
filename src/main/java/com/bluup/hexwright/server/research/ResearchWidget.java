package com.bluup.hexwright.server.research;

import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class ResearchWidget extends Widget implements IConfigurableWidget {

    public static final String EDITOR_GROUP = "widget.research_table";

    private @Nullable ResearchTableBlockEntity table;

    protected ResearchWidget(int width, int height) {
        super(0, 0, width, height);
    }

    void bind(ResearchTableBlockEntity table) {
        this.table = table;
    }

    protected @Nullable ResearchTableBlockEntity table() {
        return table;
    }

    protected ResearchSession session() {
        return table != null ? table.session() : Preview.SESSION;
    }

    protected @Nullable ServerPlayer sender() {
        return gui != null && gui.entityPlayer instanceof ServerPlayer player ? player : null;
    }

    private static final class Preview {
        static final ResearchSession SESSION = create();

        private static ResearchSession create() {
            ResearchSession session = new ResearchSession();
            session.setTablet(20261017L, TabletDamage.EFFACED);
            session.select(2);
            session.begin(new UUID(0, 0), RandomSource.create(20261017L));
            return session;
        }
    }
}
