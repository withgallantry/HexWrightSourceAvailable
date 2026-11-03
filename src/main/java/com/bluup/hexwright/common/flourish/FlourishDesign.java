package com.bluup.hexwright.common.flourish;

import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record FlourishDesign(List<Layer> layers, @Nullable Flourish chime, Motes motes) {

    public static final int MAX_LAYERS = 4;

    private static final float[] SCALES = {0.5f, 0.75f, 1.0f, 1.5f, 2.0f};
    public static final int SIZES = SCALES.length;
    public static final int DEFAULT_SIZE = 2;

    public static final int MAX_DELAY = 10;
    public static final int DELAY_TICKS = 2;

    public static final int SHADES = 4;

    private static final String LAYERS = "Layers";
    private static final String MOTIF = "Motif";
    private static final String PLACEMENT = "Placement";
    private static final String SIZE = "Size";
    private static final String DELAY = "Delay";
    private static final String SHADE = "Shade";
    private static final String FIGURE = "Figure";
    private static final String CHIME = "Chime";
    private static final String MOTES = "Motes";

    public static final FlourishDesign DEFAULT =
        new FlourishDesign(List.of(Layer.of(FlourishMotif.SEAL)), Flourish.HEXAGRAM, Motes.RING);

    public FlourishDesign {
        layers = List.copyOf(layers.size() > MAX_LAYERS ? layers.subList(0, MAX_LAYERS) : layers);
    }

    public record Layer(FlourishMotif motif, FlourishMotif.Placement placement, int size, int delay, int shade,
                        @Nullable FlourishFigure figure) {
        public Layer {
            if (!motif.suits(placement)) {
                placement = motif.defaultPlacement();
            }
            size = Mth.clamp(size, 0, SIZES - 1);
            delay = Mth.clamp(delay, 0, MAX_DELAY);
            shade = Math.floorMod(shade, SHADES);
            if (motif != FlourishMotif.FIGURE) {
                figure = null;
            } else if (figure == null) {
                figure = FlourishFigure.DEFAULT;
            }
        }

        public Layer(FlourishMotif motif, FlourishMotif.Placement placement, int size, int delay, int shade) {
            this(motif, placement, size, delay, shade, null);
        }

        public static Layer of(FlourishMotif motif) {
            return new Layer(motif, motif.defaultPlacement(), DEFAULT_SIZE, 0, 0);
        }

        public Layer withMotif(FlourishMotif motif) {
            return new Layer(motif, placement, size, delay, shade, figure);
        }

        public Layer withPlacement(FlourishMotif.Placement placement) {
            return new Layer(motif, placement, size, delay, shade, figure);
        }

        public Layer withSize(int size) {
            return new Layer(motif, placement, size, delay, shade, figure);
        }

        public Layer withDelay(int delay) {
            return new Layer(motif, placement, size, delay, shade, figure);
        }

        public Layer withShade(int shade) {
            return new Layer(motif, placement, size, delay, shade, figure);
        }

        public Layer withFigure(FlourishFigure figure) {
            return new Layer(FlourishMotif.FIGURE, placement, size, delay, shade, figure);
        }

        public float scale() {
            return SCALES[size];
        }

        public int delayTicks() {
            return delay * DELAY_TICKS;
        }
    }

    public enum Motes {
        NONE("none"),
        BURST("burst"),
        RING("ring"),
        RISE("rise"),
        FALL("fall"),
        SPIRAL("spiral");

        private final String id;

        Motes(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "flourish_motes.hexwright." + id;
        }

        public static Motes byId(String id) {
            for (Motes motes : values()) {
                if (motes.id.equals(id)) {
                    return motes;
                }
            }
            return NONE;
        }
    }

    public FlourishDesign withLayers(List<Layer> layers) {
        return new FlourishDesign(layers, chime, motes);
    }

    public Set<Flourish> sources() {
        Set<Flourish> sources = EnumSet.noneOf(Flourish.class);
        for (Layer layer : layers) {
            if (layer.motif().source() != null) {
                sources.add(layer.motif().source());
            }
        }
        if (chime != null) {
            sources.add(chime);
        }
        return sources;
    }

    public Rarity rarity() {
        Rarity rarest = Rarity.UNCOMMON;
        for (Flourish source : sources()) {
            if (source.rarity().ordinal() > rarest.ordinal()) {
                rarest = source.rarity();
            }
        }
        return rarest;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Layer layer : layers) {
            CompoundTag entry = new CompoundTag();
            entry.putString(MOTIF, layer.motif().id());
            entry.putString(PLACEMENT, layer.placement().id());
            entry.putByte(SIZE, (byte) layer.size());
            entry.putByte(DELAY, (byte) layer.delay());
            entry.putByte(SHADE, (byte) layer.shade());
            if (layer.figure() != null) {
                entry.put(FIGURE, layer.figure().save());
            }
            list.add(entry);
        }
        tag.put(LAYERS, list);
        if (chime != null) {
            tag.putString(CHIME, chime.id());
        }
        tag.putString(MOTES, motes.id());
        return tag;
    }

    public static @Nullable FlourishDesign load(CompoundTag tag) {
        List<Layer> layers = new ArrayList<>();
        ListTag list = tag.getList(LAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && layers.size() < MAX_LAYERS; i++) {
            CompoundTag entry = list.getCompound(i);
            FlourishMotif motif = FlourishMotif.byId(entry.getString(MOTIF));
            if (motif == null) {
                continue;
            }
            FlourishMotif.Placement placement = FlourishMotif.Placement.byId(entry.getString(PLACEMENT));
            layers.add(new Layer(motif, placement != null ? placement : motif.defaultPlacement(),
                entry.contains(SIZE) ? entry.getByte(SIZE) : DEFAULT_SIZE, entry.getByte(DELAY), entry.getByte(SHADE),
                FlourishFigure.load(entry.getCompound(FIGURE))));
        }
        if (layers.isEmpty()) {
            return null;
        }
        Flourish chime = tag.contains(CHIME) ? Flourish.byId(tag.getString(CHIME)) : null;
        return new FlourishDesign(layers, chime, Motes.byId(tag.getString(MOTES)));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(layers.size());
        for (Layer layer : layers) {
            buf.writeEnum(layer.motif());
            buf.writeEnum(layer.placement());
            buf.writeByte(layer.size());
            buf.writeByte(layer.delay());
            buf.writeByte(layer.shade());
            buf.writeNullable(layer.figure(), (out, figure) -> figure.write(out));
        }
        buf.writeBoolean(chime != null);
        if (chime != null) {
            buf.writeEnum(chime);
        }
        buf.writeEnum(motes);
    }

    public static FlourishDesign read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 1 || count > MAX_LAYERS) {
            throw new DecoderException("A flourish design has 1 to " + MAX_LAYERS + " layers, not " + count);
        }
        List<Layer> layers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FlourishMotif motif = buf.readEnum(FlourishMotif.class);
            FlourishMotif.Placement placement = buf.readEnum(FlourishMotif.Placement.class);
            layers.add(new Layer(motif, placement, buf.readByte(), buf.readByte(), buf.readByte(),
                buf.readNullable(FlourishFigure::read)));
        }
        Flourish chime = buf.readBoolean() ? buf.readEnum(Flourish.class) : null;
        return new FlourishDesign(layers, chime, buf.readEnum(Motes.class));
    }
}
