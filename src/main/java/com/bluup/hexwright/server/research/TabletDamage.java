package com.bluup.hexwright.server.research;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

public enum TabletDamage implements StringRepresentable {

    WEATHERED("weathered", false, 4, 2, 2, 2, 4, 0.10f, 1, 8),
    SCARRED("scarred", false, 6, 2, 3, 2, 7, 0.18f, 1, 6),
    EFFACED("effaced", false, 8, 2, 3, 1, 10, 0.28f, 2, 4),
    SHATTERED("shattered", false, 10, 3, 3, 1, 14, 0.38f, 3, 2),

    SUNDERED("sundered", true, 8, 2, 2, 2, 8, 0.14f, 2, 8),
    RIVEN("riven", true, 10, 2, 3, 2, 10, 0.22f, 2, 6),
    RAVAGED("ravaged", true, 12, 2, 3, 1, 12, 0.30f, 3, 4),
    OBLITERATED("obliterated", true, 14, 3, 3, 1, 14, 0.40f, 3, 2);

    private final String name;
    private final boolean artifact;
    private final int breaks;
    private final int minRun;
    private final int maxRun;
    private final int context;
    private final int decoys;
    private final float pitting;
    private final int suggestedLevels;
    private final int weight;

    TabletDamage(String name, boolean artifact, int breaks, int minRun, int maxRun, int context, int decoys,
                 float pitting, int suggestedLevels, int weight) {
        this.name = name;
        this.artifact = artifact;
        this.breaks = breaks;
        this.minRun = minRun;
        this.maxRun = maxRun;
        this.context = context;
        this.decoys = decoys;
        this.pitting = pitting;
        this.suggestedLevels = suggestedLevels;
        this.weight = weight;
    }

    public boolean isArtifact() {
        return artifact;
    }

    public int breaks() {
        return breaks;
    }

    public int minRun() {
        return minRun;
    }

    public int maxRun() {
        return maxRun;
    }

    public int context() {
        return context;
    }

    public int decoys() {
        return decoys;
    }

    public float pitting() {
        return pitting;
    }

    public int inspirationPerStep() {
        return artifact ? 2 : 1;
    }

    public int suggestedLevels() {
        return suggestedLevels;
    }

    public Component label() {
        return Component.translatable("tablet.hexwright.damage." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static TabletDamage byName(String name, TabletDamage fallback) {
        for (TabletDamage damage : values()) {
            if (damage.name.equals(name)) {
                return damage;
            }
        }
        return fallback;
    }

    public static TabletDamage roll(RandomSource random, boolean artifact) {
        int total = 0;
        for (TabletDamage damage : values()) {
            if (damage.artifact == artifact) {
                total += damage.weight;
            }
        }
        int roll = random.nextInt(total);
        for (TabletDamage damage : values()) {
            if (damage.artifact == artifact) {
                roll -= damage.weight;
                if (roll < 0) {
                    return damage;
                }
            }
        }
        return artifact ? SUNDERED : WEATHERED;
    }
}
