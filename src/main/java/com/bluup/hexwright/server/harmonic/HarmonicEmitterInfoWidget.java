package com.bluup.hexwright.server.harmonic;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.network.EssenceNetwork;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class HarmonicEmitterInfoWidget extends Widget {

    private static final int UPDATE_ID = 1;

    private static final int HEADING_COLOR = 0xFFD9C48C;
    private static final int TEXT_COLOR = 0xFFC8D7FF;
    private static final int DIM_COLOR = 0xFF8895AA;
    private static final int ACTIVE_COLOR = 0xFF9BD87A;
    private static final int INACTIVE_COLOR = 0xFFE0736B;

    public record Snapshot(
        boolean keyed,
        boolean attuned,
        boolean inRange,
        boolean tuned,
        boolean exchangeActive,
        String networkName,
        int harmonic,
        int hexLength,
        long media,
        long capacity,
        int signalsLastMinute,
        String message
    ) {
        public static Snapshot empty(long media, long capacity) {
            return new Snapshot(false, false, true, false, false, "", 0, 0, media, capacity, 0, "");
        }

        public static Snapshot unattuned(boolean keyed, long media, long capacity) {
            return new Snapshot(keyed, false, true, false, false, "", 0, 0, media, capacity, 0, "");
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(keyed);
            buf.writeBoolean(attuned);
            buf.writeBoolean(inRange);
            buf.writeBoolean(tuned);
            buf.writeBoolean(exchangeActive);
            buf.writeUtf(networkName, 64);
            buf.writeVarInt(harmonic);
            buf.writeVarInt(hexLength);
            buf.writeVarLong(media);
            buf.writeVarLong(capacity);
            buf.writeVarInt(signalsLastMinute);
            buf.writeUtf(message, 256);
        }

        public static Snapshot read(FriendlyByteBuf buf) {
            return new Snapshot(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readUtf(256)
            );
        }

        public boolean matches(Snapshot other) {
            return keyed == other.keyed
                && attuned == other.attuned
                && inRange == other.inRange
                && tuned == other.tuned
                && exchangeActive == other.exchangeActive
                && harmonic == other.harmonic
                && hexLength == other.hexLength
                && media == other.media
                && capacity == other.capacity
                && signalsLastMinute == other.signalsLastMinute
                && Objects.equals(networkName, other.networkName)
                && Objects.equals(message, other.message);
        }
    }

    private final Supplier<Snapshot> source;

    private Snapshot snapshot = Snapshot.empty(0L, 0L);

    public HarmonicEmitterInfoWidget(int x, int y, int width, int height, Supplier<Snapshot> source) {
        super(x, y, width, height);
        this.source = source;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buf) {
        super.writeInitialData(buf);
        snapshot = source == null ? Snapshot.empty(0L, 0L) : source.get();
        snapshot.write(buf);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buf) {
        super.readInitialData(buf);
        snapshot = Snapshot.read(buf);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (source == null) {
            return;
        }
        Snapshot fresh = source.get();
        if (snapshot.matches(fresh)) {
            return;
        }
        snapshot = fresh;
        writeUpdateInfo(UPDATE_ID, fresh::write);
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buf) {
        if (id == UPDATE_ID) {
            snapshot = Snapshot.read(buf);
        } else {
            super.readUpdateInfo(id, buf);
        }
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);

        Font font = Minecraft.getInstance().font;
        int left = getPosition().x + 5;
        int top = getPosition().y + 5;
        int maxLines = InfoPanel.visibleLines(getSize().height);
        int maxTextWidth = InfoPanel.maxTextWidth(getSize().width);

        int drawn = 0;
        for (InfoPanel.Line line : lines()) {
            if (drawn >= maxLines) {
                break;
            }
            InfoPanel.draw(graphics, font, InfoPanel.clip(font, line.text(), maxTextWidth), left, top + drawn * InfoPanel.LINE_STEP, line.color());
            drawn++;
        }
    }

    private List<InfoPanel.Line> lines() {
        List<InfoPanel.Line> lines = new ArrayList<>();

        Component status;
        int statusColor;
        if (!snapshot.keyed()) {
            status = Component.translatable("gui.hexwright.harmonic_emitter.status.no_key");
            statusColor = DIM_COLOR;
        } else if (!snapshot.attuned()) {
            status = Component.translatable("gui.hexwright.harmonic_emitter.status.key_unattuned");
            statusColor = INACTIVE_COLOR;
        } else if (!snapshot.inRange()) {
            status = Component.translatable("gui.hexwright.harmonic_emitter.status.out_of_range",
                EssenceNetwork.keyRange());
            statusColor = INACTIVE_COLOR;
        } else if (!snapshot.tuned()) {
            status = Component.translatable("gui.hexwright.harmonic_emitter.status.untuned");
            statusColor = DIM_COLOR;
        } else if (!snapshot.exchangeActive()) {
            status = Component.translatable("gui.hexwright.harmonic_emitter.status.no_exchange");
            statusColor = INACTIVE_COLOR;
        } else if (snapshot.media() <= 0L) {
            status = Component.translatable("gui.hexwright.harmonic_emitter.status.no_media");
            statusColor = INACTIVE_COLOR;
        } else {
            status = Component.translatable("gui.hexwright.harmonic_emitter.status.listening");
            statusColor = ACTIVE_COLOR;
        }
        lines.add(new InfoPanel.Line(
            Component.translatable("gui.hexwright.harmonic_emitter.status", status).getString(), statusColor));

        if (snapshot.attuned()) {
            String network = snapshot.networkName().isEmpty()
                ? Component.translatable("gui.hexwright.harmonic_emitter.network.unnamed").getString()
                : snapshot.networkName();
            lines.add(new InfoPanel.Line(
                Component.translatable("gui.hexwright.harmonic_emitter.network", network).getString(), TEXT_COLOR));
        }
        if (snapshot.tuned()) {
            lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_emitter.channel",
                snapshot.harmonic()).getString(), TEXT_COLOR));
            lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_emitter.hex",
                snapshot.hexLength()).getString(), TEXT_COLOR));
        } else {
            lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_emitter.untuned_hint")
                .withStyle(ChatFormatting.ITALIC).getString(), DIM_COLOR));
        }

        lines.add(new InfoPanel.Line("", TEXT_COLOR));
        lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_emitter.media",
            formatDust(snapshot.media()), formatDust(snapshot.capacity())).getString(),
            snapshot.media() <= 0L ? INACTIVE_COLOR : TEXT_COLOR));

        lines.add(new InfoPanel.Line("", TEXT_COLOR));
        lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_emitter.signals").getString(),
            HEADING_COLOR));
        lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_emitter.last_minute",
            snapshot.signalsLastMinute()).getString(), TEXT_COLOR));

        if (!snapshot.message().isEmpty()) {
            lines.add(new InfoPanel.Line("", TEXT_COLOR));
            lines.add(new InfoPanel.Line(snapshot.message(), DIM_COLOR));
        }
        return lines;
    }

    private static String formatDust(long media) {
        return String.format("%.1f", media / (double) MediaConstants.DUST_UNIT);
    }

}
