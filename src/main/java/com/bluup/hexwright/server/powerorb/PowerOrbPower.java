package com.bluup.hexwright.server.powerorb;

public enum PowerOrbPower {
    GOLEM("golem", 0x5FC681),
    SANCTUARY("sanctuary", 0xC8E65A);

    private final String id;
    private final int defaultColour;

    PowerOrbPower(String id, int defaultColour) {
        this.id = id;
        this.defaultColour = defaultColour;
    }

    public String id() {
        return this.id;
    }

    public int defaultColour() {
        return this.defaultColour;
    }

    public String subtitleKey() {
        return "tooltip.hexwright.power_orb." + this.id;
    }
}
