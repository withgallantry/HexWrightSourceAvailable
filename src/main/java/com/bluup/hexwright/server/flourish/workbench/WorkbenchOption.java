package com.bluup.hexwright.server.flourish.workbench;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum WorkbenchOption {
    PLACEMENT,
    SIZE,
    DELAY,
    SHADE,
    CHIME,
    MOTES,
    COUNT,
    FORM,
    RADIUS,
    WEIGHT,
    SPIN,
    TURN,
    HUE,
    ENTRANCE,
    EXIT,
    TEMPO;

    public boolean ofFigure() {
        return ordinal() >= COUNT.ordinal();
    }

    public boolean hasTooltip() {
        return this != DELAY && this != CHIME;
    }

    private String key() {
        return "gui.hexwright.flourish_workbench.option." + name().toLowerCase(Locale.ROOT);
    }

    public Component label() {
        return Component.translatable(key());
    }

    public Component hint() {
        return Component.translatable(key() + ".hint");
    }
}
