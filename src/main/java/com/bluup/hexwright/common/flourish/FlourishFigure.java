package com.bluup.hexwright.common.flourish;

import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record FlourishFigure(List<Ring> rings, Entrance entrance, Exit exit, int tempo) {

    public static final int MAX_RINGS = 6;

    public static final int MAX_COUNT = 12;
    public static final int FORMS = 4;
    public static final int RADII = 10;
    public static final int WEIGHTS = 4;
    public static final int MAX_SPIN = 3;
    public static final int TURNS = 4;
    public static final int HUES = 4;

    private static final int[] LIFETIMES = {20, 28, 36, 46, 60};
    public static final int TEMPOS = LIFETIMES.length;
    public static final int DEFAULT_TEMPO = 2;

    private static final String RINGS = "Rings";
    private static final String SHAPE = "Shape";
    private static final String COUNT = "Count";
    private static final String FORM = "Form";
    private static final String RADIUS = "Radius";
    private static final String WEIGHT = "Weight";
    private static final String SPIN = "Spin";
    private static final String TURN = "Turn";
    private static final String HUE = "Hue";
    private static final String ENTRANCE = "Entrance";
    private static final String EXIT = "Exit";
    private static final String TEMPO = "Tempo";

    public static final FlourishFigure DEFAULT = new FlourishFigure(List.of(
        new Ring(Shape.CIRCLE, 6, 1, 10, 1, 0, 0, 0),
        new Ring(Shape.STAR, 5, 0, 9, 1, 1, 0, 1),
        new Ring(Shape.BEADS, 5, 1, 4, 1, -1, 2, 2)),
        Entrance.TRACE, Exit.FADE, DEFAULT_TEMPO);

    public FlourishFigure {
        rings = List.copyOf(rings.size() > MAX_RINGS ? rings.subList(0, MAX_RINGS) : rings);
        tempo = Mth.clamp(tempo, 0, TEMPOS - 1);
    }

    public static FlourishFigure of(Shape shape) {
        return new FlourishFigure(List.of(Ring.of(shape).withRadius(RADII)), Entrance.STAMP, Exit.FADE, DEFAULT_TEMPO);
    }

    public static FlourishFigure of(Ring ring) {
        return new FlourishFigure(List.of(ring), Entrance.STAMP, Exit.FADE, DEFAULT_TEMPO);
    }

    public int lifetimeTicks() {
        return LIFETIMES[tempo];
    }

    public FlourishFigure withRings(List<Ring> rings) {
        return new FlourishFigure(rings, entrance, exit, tempo);
    }

    public FlourishFigure withRing(int index, Ring ring) {
        List<Ring> changed = new ArrayList<>(rings);
        changed.set(index, ring);
        return withRings(changed);
    }

    public FlourishFigure withEntrance(Entrance entrance) {
        return new FlourishFigure(rings, entrance, exit, tempo);
    }

    public FlourishFigure withExit(Exit exit) {
        return new FlourishFigure(rings, entrance, exit, tempo);
    }

    public FlourishFigure withTempo(int tempo) {
        return new FlourishFigure(rings, entrance, exit, tempo);
    }

    public record Ring(Shape shape, int count, int form, int radius, int weight, int spin, int turn, int hue) {
        public Ring {
            count = Mth.clamp(count, shape.minCount(), MAX_COUNT);
            form = Mth.clamp(form, 0, shape.forms(count) - 1);
            radius = Mth.clamp(radius, 1, RADII);
            weight = Mth.clamp(weight, 0, WEIGHTS - 1);
            spin = Mth.clamp(spin, -MAX_SPIN, MAX_SPIN);
            turn = Math.floorMod(turn, TURNS);
            hue = Math.floorMod(hue, HUES);
        }

        public static Ring of(Shape shape) {
            return new Ring(shape, shape.defaultCount(), 0, 7, 1, 0, 0, 0);
        }

        public Ring withShape(Shape shape) {
            return new Ring(shape, count, form, radius, weight, spin, turn, hue);
        }

        public Ring withCount(int count) {
            return new Ring(shape, count, form, radius, weight, spin, turn, hue);
        }

        public Ring withForm(int form) {
            return new Ring(shape, count, form, radius, weight, spin, turn, hue);
        }

        public Ring withRadius(int radius) {
            return new Ring(shape, count, form, radius, weight, spin, turn, hue);
        }

        public Ring withWeight(int weight) {
            return new Ring(shape, count, form, radius, weight, spin, turn, hue);
        }

        public Ring withSpin(int spin) {
            return new Ring(shape, count, form, radius, weight, spin, turn, hue);
        }

        public Ring withTurn(int turn) {
            return new Ring(shape, count, form, radius, weight, spin, turn, hue);
        }

        public Ring withHue(int hue) {
            return new Ring(shape, count, form, radius, weight, spin, turn, hue);
        }

        public boolean counted() {
            return shape != Shape.CIRCLE || form == DASHED_CIRCLE;
        }
    }

    private static final int DASHED_CIRCLE = 2;

    public enum Shape {
        CIRCLE("circle", 3, 6),
        POLYGON("polygon", 3, 6),
        STAR("star", 5, 5),
        ROSE("rose", 3, 6),
        RAYS("rays", 3, 8),
        BEADS("beads", 3, 8),
        LOOPS("loops", 3, 6),
        TEETH("teeth", 3, 8),
        WAVE("wave", 3, 8),
        GLYPHS("glyphs", 3, 10),
        SPIRAL("spiral", 3, 3),
        GRID("grid", 3, 6);

        private final String id;
        private final int minCount;
        private final int defaultCount;

        Shape(String id, int minCount, int defaultCount) {
            this.id = id;
            this.minCount = minCount;
            this.defaultCount = defaultCount;
        }

        public String id() {
            return id;
        }

        public int minCount() {
            return minCount;
        }

        public int defaultCount() {
            return defaultCount;
        }

        public int forms(int count) {
            return this == STAR ? Math.max(1, (count - 1) / 2 - 1) : FORMS;
        }

        public String translationKey() {
            return "flourish_shape.hexwright." + id;
        }

        public static @Nullable Shape byId(String id) {
            for (Shape shape : values()) {
                if (shape.id.equals(id)) {
                    return shape;
                }
            }
            return null;
        }
    }

    public enum Entrance {
        STAMP("stamp"),
        UNFOLD("unfold"),
        TRACE("trace"),
        CASCADE("cascade"),
        CONVERGE("converge");

        private final String id;

        Entrance(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "flourish_entrance.hexwright." + id;
        }

        public static Entrance byId(String id) {
            for (Entrance entrance : values()) {
                if (entrance.id.equals(id)) {
                    return entrance;
                }
            }
            return STAMP;
        }
    }

    public enum Exit {
        FADE("fade"),
        SHATTER("shatter"),
        COLLAPSE("collapse"),
        BURST("burst"),
        UNWIND("unwind");

        private final String id;

        Exit(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "flourish_exit.hexwright." + id;
        }

        public static Exit byId(String id) {
            for (Exit exit : values()) {
                if (exit.id.equals(id)) {
                    return exit;
                }
            }
            return FADE;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Ring ring : rings) {
            CompoundTag entry = new CompoundTag();
            entry.putString(SHAPE, ring.shape().id());
            entry.putByte(COUNT, (byte) ring.count());
            entry.putByte(FORM, (byte) ring.form());
            entry.putByte(RADIUS, (byte) ring.radius());
            entry.putByte(WEIGHT, (byte) ring.weight());
            entry.putByte(SPIN, (byte) ring.spin());
            entry.putByte(TURN, (byte) ring.turn());
            entry.putByte(HUE, (byte) ring.hue());
            list.add(entry);
        }
        tag.put(RINGS, list);
        tag.putString(ENTRANCE, entrance.id());
        tag.putString(EXIT, exit.id());
        tag.putByte(TEMPO, (byte) tempo);
        return tag;
    }

    public static @Nullable FlourishFigure load(CompoundTag tag) {
        List<Ring> rings = new ArrayList<>();
        ListTag list = tag.getList(RINGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && rings.size() < MAX_RINGS; i++) {
            CompoundTag entry = list.getCompound(i);
            Shape shape = Shape.byId(entry.getString(SHAPE));
            if (shape == null) {
                continue;
            }
            rings.add(new Ring(shape, entry.getByte(COUNT), entry.getByte(FORM), entry.getByte(RADIUS),
                entry.getByte(WEIGHT), entry.getByte(SPIN), entry.getByte(TURN), entry.getByte(HUE)));
        }
        if (rings.isEmpty()) {
            return null;
        }
        return new FlourishFigure(rings, Entrance.byId(tag.getString(ENTRANCE)), Exit.byId(tag.getString(EXIT)),
            tag.contains(TEMPO) ? tag.getByte(TEMPO) : DEFAULT_TEMPO);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(rings.size());
        for (Ring ring : rings) {
            buf.writeEnum(ring.shape());
            buf.writeByte(ring.count());
            buf.writeByte(ring.form());
            buf.writeByte(ring.radius());
            buf.writeByte(ring.weight());
            buf.writeByte(ring.spin());
            buf.writeByte(ring.turn());
            buf.writeByte(ring.hue());
        }
        buf.writeEnum(entrance);
        buf.writeEnum(exit);
        buf.writeByte(tempo);
    }

    public static FlourishFigure read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 1 || count > MAX_RINGS) {
            throw new DecoderException("A flourish figure has 1 to " + MAX_RINGS + " rings, not " + count);
        }
        List<Ring> rings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rings.add(new Ring(buf.readEnum(Shape.class), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(),
                buf.readByte(), buf.readByte(), buf.readByte()));
        }
        return new FlourishFigure(rings, buf.readEnum(Entrance.class), buf.readEnum(Exit.class), buf.readByte());
    }
}
