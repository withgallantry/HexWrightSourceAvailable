package com.bluup.hexwright.client.hexweave;

import net.minecraft.network.chat.Component;

public enum LoomAction {
    ADD("add"),
    ADD_TRIGGER("add_trigger"),
    OPEN("open"),
    DELETE("delete"),
    UNLINK("unlink"),
    FIT("fit"),
    ZOOM_IN("zoom_in"),
    ZOOM_OUT("zoom_out"),
    BACK("back"),
    UNDO("undo"),
    REDO("redo"),
    REMOVE("remove"),
    CLEAR("clear"),
    COPY("copy"),
    SAVE("save"),
    CLOSE("close");

    private final String labelKey;

    LoomAction(String label) {
        this.labelKey = "gui.hexwright.hex_loom." + label;
    }

    public Component label() {
        return Component.translatable(labelKey);
    }
}
