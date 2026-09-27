package com.bluup.hexwright.server.powerorb;

public enum PowerOrbPower {
    GOLEM("golem", 0x5FC681, true),
    CAT("cat", 0x3AE0B0, true),
    GUARDIAN("guardian", 0x2F9EED, true),
    SANCTUARY("sanctuary", 0xC8E65A, true),
    TEETH("teeth", 0x7B4FD8, true),
    DUST("dust", 0xA487B3, false);

    private final String id;
    private final int defaultColour;
    private final boolean bindsSigil;

    PowerOrbPower(String id, int defaultColour, boolean bindsSigil) {
        this.id = id;
        this.defaultColour = defaultColour;
        this.bindsSigil = bindsSigil;
    }

    public String id() {
        return this.id;
    }

    public int defaultColour() {
        return this.defaultColour;
    }

    public boolean bindsSigil() {
        return this.bindsSigil;
    }

    public String subtitleKey() {
        return "tooltip.hexwright.power_orb." + this.id;
    }
}
