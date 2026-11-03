package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.common.flourish.FlourishFigure;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum WorkbenchCaption {
    MOTIF,
    LAYER,
    FLOURISH,
    RING,
    SHAPE,
    RINGS;

    private String key() {
        return "gui.hexwright.flourish_workbench.caption." + name().toLowerCase(Locale.ROOT);
    }

    @Nullable Component text(FlourishDraft draft) {
        Component layer = Component.literal(WorkbenchTabWidget.NUMERALS[draft.selected()]);
        FlourishFigure.Ring ring = draft.ring(draft.selectedRing());
        Component ringNumber = Component.literal(WorkbenchTabWidget.NUMERALS[draft.selectedRing()]);
        return switch (this) {
            case MOTIF -> Component.translatable(key(), layer);
            case LAYER -> Component.translatable(key(), layer,
                Component.translatable(draft.selectedLayer().motif().translationKey()));
            case FLOURISH, RINGS -> Component.translatable(key());
            case RING -> ring == null ? null
                : Component.translatable(key(), ringNumber, Component.translatable(ring.shape().translationKey()));
            case SHAPE -> ring == null ? Component.translatable(key() + ".new") : Component.translatable(key(), ringNumber);
        };
    }

    boolean faint(FlourishDraft draft) {
        return this == RINGS && draft.figure() == null;
    }
}
