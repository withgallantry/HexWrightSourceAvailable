package com.bluup.hexwright.common.flourish;

import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

public enum Flourish {
    HEXAGRAM("hexagram", Rarity.UNCOMMON),
    LATTICE("lattice", Rarity.UNCOMMON),
    NOVA("nova", Rarity.UNCOMMON),
    THREAD("thread", Rarity.UNCOMMON),
    WATCHER("watcher", Rarity.UNCOMMON),
    TIDE("tide", Rarity.UNCOMMON),
    GEODE("geode", Rarity.UNCOMMON),
    WEB("web", Rarity.UNCOMMON),
    RIME("rime", Rarity.UNCOMMON),
    MOTHS("moths", Rarity.UNCOMMON),
    CONSTELLATION("constellation", Rarity.UNCOMMON),
    AURORA("aurora", Rarity.UNCOMMON),
    DANDELION("dandelion", Rarity.UNCOMMON),
    MYCELIUM("mycelium", Rarity.UNCOMMON),
    SIGIL("sigil", Rarity.RARE),
    PILLAR("pillar", Rarity.RARE),
    ORRERY("orrery", Rarity.RARE),
    BLOSSOM("blossom", Rarity.RARE),
    CROWN("crown", Rarity.RARE),
    STARFALL("starfall", Rarity.RARE),
    CLOCKWORK("clockwork", Rarity.RARE),
    PYRE("pyre", Rarity.RARE),
    LABYRINTH("labyrinth", Rarity.RARE),
    FETTERS("fetters", Rarity.RARE),
    ARSENAL("arsenal", Rarity.RARE),
    EFFIGY("effigy", Rarity.RARE),
    PRISM("prism", Rarity.RARE),
    WILDFLOWER("wildflower", Rarity.RARE),
    BRIAR("briar", Rarity.RARE),
    AEGIS("aegis", Rarity.EPIC),
    MAELSTROM("maelstrom", Rarity.EPIC),
    TEMPEST("tempest", Rarity.EPIC),
    ECLIPSE("eclipse", Rarity.EPIC),
    SERAPH("seraph", Rarity.EPIC),
    TESSERACT("tesseract", Rarity.EPIC),
    OUROBOROS("ouroboros", Rarity.EPIC),
    RIFT("rift", Rarity.EPIC),
    SINGULARITY("singularity", Rarity.EPIC),
    ROSE_WINDOW("rose_window", Rarity.EPIC),
    WORLD_TREE("world_tree", Rarity.EPIC);

    private final String id;
    private final Rarity rarity;

    Flourish(String id, Rarity rarity) {
        this.id = id;
        this.rarity = rarity;
    }

    public String id() {
        return id;
    }

    public Rarity rarity() {
        return rarity;
    }

    public String itemId() {
        return "flourish_" + id;
    }

    public String nameKey() {
        return "flourish.hexwright." + id;
    }

    public static @Nullable Flourish byId(String id) {
        for (Flourish flourish : values()) {
            if (flourish.id.equals(id)) {
                return flourish;
            }
        }
        return null;
    }
}
