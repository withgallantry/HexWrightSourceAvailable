package com.bluup.hexwright.server.journal;

public enum JournalToastKind {

    STRUCTURE,

    INVESTIGATION,

    LORE,

    ARTIFACT;

    private static final JournalToastKind[] BY_ID = values();

    public byte id() {
        return (byte) ordinal();
    }

    public static JournalToastKind byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : INVESTIGATION;
    }
}
