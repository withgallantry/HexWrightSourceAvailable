package com.bluup.hexwright.common.flourish;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public enum FlourishMotif {
    SEAL("seal", Kind.DISC, Flourish.HEXAGRAM),
    LATTICE("lattice", Kind.DISC, Flourish.LATTICE),
    RING("ring", Kind.DISC, Flourish.NOVA),
    RIPPLE("ripple", Kind.DISC, Flourish.TIDE),
    ORBIT("orbit", Kind.DISC, Flourish.ORRERY),
    PETALS("petals", Kind.DISC, Flourish.BLOSSOM),
    GEARS("gears", Kind.DISC, Flourish.CLOCKWORK),
    VORTEX("vortex", Kind.DISC, Flourish.MAELSTROM),
    WEB("web", Kind.DISC, Flourish.WEB),
    FROST("frost", Kind.DISC, Flourish.RIME),
    MAZE("maze", Kind.DISC, Flourish.LABYRINTH),
    SERPENT("serpent", Kind.DISC, Flourish.OUROBOROS),
    STAR("star", Kind.FACING, Flourish.NOVA),
    EYE("eye", Kind.FACING, Flourish.WATCHER),
    SHARD("shard", Kind.FACING, Flourish.GEODE),
    SIGIL("sigil", Kind.FACING, Flourish.SIGIL),
    WARD("ward", Kind.FACING, Flourish.AEGIS),
    DARK_SUN("dark_sun", Kind.FACING, Flourish.ECLIPSE),
    WINGS("wings", Kind.FACING, Flourish.SERAPH),
    MOTH("moth", Kind.FACING, Flourish.MOTHS),
    CONSTELLATION("constellation", Kind.FACING, Flourish.CONSTELLATION),
    TESSERACT("tesseract", Kind.FACING, Flourish.TESSERACT),
    RIFT("rift", Kind.FACING, Flourish.RIFT),
    THREAD("thread", Kind.AXIAL, Flourish.THREAD),
    BEAM("beam", Kind.AXIAL, Flourish.PILLAR),
    STREAK("streak", Kind.AXIAL, Flourish.STARFALL),
    LIGHTNING("lightning", Kind.AXIAL, Flourish.TEMPEST),
    BLADE("blade", Kind.AXIAL, Flourish.ARSENAL),
    GLYPHS("glyphs", Kind.BAND, Flourish.CROWN),
    FLAME("flame", Kind.BAND, Flourish.PYRE),
    CHAINS("chains", Kind.BAND, Flourish.FETTERS),
    FIGURE("figure", Kind.DISC, null);

    public enum Kind {
        DISC,
        FACING,
        AXIAL,
        BAND;

        public List<Placement> placements() {
            List<Placement> placements = new ArrayList<>();
            for (Placement placement : Placement.values()) {
                if (placement.kind == this) {
                    placements.add(placement);
                }
            }
            return placements;
        }
    }

    public enum Placement {
        GROUND("ground", Kind.DISC),
        LEVEL("level", Kind.DISC),
        STANDING("standing", Kind.DISC),
        OVERHEAD("overhead", Kind.DISC),
        CENTRE("centre", Kind.FACING),
        ABOVE("above", Kind.FACING),
        SCATTER("scatter", Kind.FACING),
        RISING("rising", Kind.AXIAL),
        FROM_HAND("from_hand", Kind.AXIAL),
        SKYFALL("skyfall", Kind.AXIAL),
        BURST("burst", Kind.AXIAL),
        AROUND("around", Kind.BAND),
        FOOT("foot", Kind.BAND);

        private final String id;
        private final Kind kind;

        Placement(String id, Kind kind) {
            this.id = id;
            this.kind = kind;
        }

        public String id() {
            return id;
        }

        public Kind kind() {
            return kind;
        }

        public String translationKey() {
            return "flourish_placement.hexwright." + id;
        }

        public static @Nullable Placement byId(String id) {
            for (Placement placement : values()) {
                if (placement.id.equals(id)) {
                    return placement;
                }
            }
            return null;
        }
    }

    private final String id;
    private final Kind kind;
    @Nullable
    private final Flourish source;

    FlourishMotif(String id, Kind kind, @Nullable Flourish source) {
        this.id = id;
        this.kind = kind;
        this.source = source;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public @Nullable Flourish source() {
        return source;
    }

    public List<Placement> placements() {
        if (this == STREAK || this == BLADE) {
            return new ArrayList<>(List.of(Placement.SKYFALL, Placement.FROM_HAND, Placement.BURST));
        }
        List<Placement> placements = kind.placements();
        if (this == FIGURE) {
            placements.addAll(Kind.FACING.placements());
        }
        return placements;
    }

    public Placement defaultPlacement() {
        return placements().get(0);
    }

    public boolean suits(Placement placement) {
        return placements().contains(placement);
    }

    public String translationKey() {
        return "flourish_motif.hexwright." + id;
    }

    public static @Nullable FlourishMotif byId(String id) {
        for (FlourishMotif motif : values()) {
            if (motif.id.equals(id)) {
                return motif;
            }
        }
        return null;
    }
}
