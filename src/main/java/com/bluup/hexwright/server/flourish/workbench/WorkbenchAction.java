package com.bluup.hexwright.server.flourish.workbench;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum WorkbenchAction {
    INSCRIBE,
    REMOVE_LAYER,
    RESET,
    REMOVE_RING,
    BACK;

    public Component label() {
        return Component.translatable("gui.hexwright.flourish_workbench.action." + name().toLowerCase(Locale.ROOT));
    }
}
