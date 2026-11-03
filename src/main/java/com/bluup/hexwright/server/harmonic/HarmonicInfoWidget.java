package com.bluup.hexwright.server.harmonic;

import com.bluup.hexwright.server.network.EssenceNetwork;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class HarmonicInfoWidget extends Widget {

    private static final int UPDATE_ID = 1;

    private static final int HEADING_COLOR = 0xFFD9C48C;
    private static final int TEXT_COLOR = 0xFFC8D7FF;
    private static final int DIM_COLOR = 0xFF8895AA;
    private static final int ACTIVE_COLOR = 0xFF9BD87A;
    private static final int INACTIVE_COLOR = 0xFFE0736B;
    private static final int TRACK_COLOR = 0x40FFFFFF;
    private static final int THUMB_COLOR = 0xFF8895AA;


    public record Snapshot(
        boolean keyed,
        boolean inRange,
        boolean active,
        int[] counts,
        boolean[] retained,
        int transmissionsLastMinute
    ) {
        public static Snapshot empty() {
            return new Snapshot(false, true, false,
                new int[HarmonicExchangeState.HARMONIC_COUNT],
                new boolean[HarmonicExchangeState.HARMONIC_COUNT], 0);
        }

        public static Snapshot outOfRange() {
            return new Snapshot(true, false, false,
                new int[HarmonicExchangeState.HARMONIC_COUNT],
                new boolean[HarmonicExchangeState.HARMONIC_COUNT], 0);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(keyed);
            buf.writeBoolean(inRange);
            buf.writeBoolean(active);
            for (int harmonic = 0; harmonic < HarmonicExchangeState.HARMONIC_COUNT; harmonic++) {
                buf.writeVarInt(counts[harmonic]);
                buf.writeBoolean(retained[harmonic]);
            }
            buf.writeVarInt(transmissionsLastMinute);
        }

        public static Snapshot read(FriendlyByteBuf buf) {
            boolean keyed = buf.readBoolean();
            boolean inRange = buf.readBoolean();
            boolean active = buf.readBoolean();
            int[] counts = new int[HarmonicExchangeState.HARMONIC_COUNT];
            boolean[] retained = new boolean[HarmonicExchangeState.HARMONIC_COUNT];
            for (int harmonic = 0; harmonic < HarmonicExchangeState.HARMONIC_COUNT; harmonic++) {
                counts[harmonic] = buf.readVarInt();
                retained[harmonic] = buf.readBoolean();
            }
            return new Snapshot(keyed, inRange, active, counts, retained, buf.readVarInt());
        }

        public int totalSubscribers() {
            int total = 0;
            for (int count : counts) {
                total += count;
            }
            return total;
        }

        public boolean matches(Snapshot other) {
            return keyed == other.keyed
                && inRange == other.inRange
                && active == other.active
                && transmissionsLastMinute == other.transmissionsLastMinute
                && java.util.Arrays.equals(counts, other.counts)
                && java.util.Arrays.equals(retained, other.retained);
        }
    }

    private final Supplier<Snapshot> source;

    private Snapshot snapshot = Snapshot.empty();

    private int scroll;

    public HarmonicInfoWidget(int x, int y, int width, int height, Supplier<Snapshot> source) {
        super(x, y, width, height);
        this.source = source;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buf) {
        super.writeInitialData(buf);
        snapshot = source == null ? Snapshot.empty() : source.get();
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
        int maxLines = visibleLines();

        List<InfoPanel.Line> lines = lines();
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - maxLines));

        for (int drawn = 0; drawn < maxLines && scroll + drawn < lines.size(); drawn++) {
            InfoPanel.Line line = lines.get(scroll + drawn);
            InfoPanel.draw(graphics, font, line.text(), left, top + drawn * InfoPanel.LINE_STEP, line.color());
        }
        drawScrollbar(graphics, lines.size(), maxLines);
    }

    private void drawScrollbar(GuiGraphics graphics, int lineCount, int maxLines) {
        if (lineCount <= maxLines) {
            return;
        }
        int x = getPosition().x + getSize().width - 3;
        int top = getPosition().y + 3;
        int height = getSize().height - 6;
        int thumbHeight = Math.max(6, height * maxLines / lineCount);
        int travel = height - thumbHeight;
        int thumbTop = top + (travel * scroll) / Math.max(1, lineCount - maxLines);
        graphics.fill(x, top, x + 1, top + height, TRACK_COLOR);
        graphics.fill(x, thumbTop, x + 1, thumbTop + thumbHeight, THUMB_COLOR);
    }

    private int visibleLines() {
        return InfoPanel.visibleLines(getSize().height);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (wheelDelta == 0.0 || !isMouseOverElement(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = Math.max(0, lines().size() - visibleLines());
        if (maxScroll == 0) {
            return false;
        }
        int next = Mth.clamp(scroll - (int) Math.signum(wheelDelta), 0, maxScroll);
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }


    private List<InfoPanel.Line> lines() {
        List<InfoPanel.Line> lines = new ArrayList<>();

        Component status;
        int statusColor;
        if (!snapshot.keyed()) {
            status = Component.translatable("gui.hexwright.harmonic_exchange.status.no_key");
            statusColor = DIM_COLOR;
        } else if (!snapshot.inRange()) {
            status = Component.translatable("gui.hexwright.harmonic_exchange.status.out_of_range",
                EssenceNetwork.keyRange());
            statusColor = INACTIVE_COLOR;
        } else if (snapshot.active()) {
            status = Component.translatable("gui.hexwright.harmonic_exchange.status.active");
            statusColor = ACTIVE_COLOR;
        } else {
            status = Component.translatable("gui.hexwright.harmonic_exchange.status.inactive");
            statusColor = INACTIVE_COLOR;
        }
        lines.add(new InfoPanel.Line(
            Component.translatable("gui.hexwright.harmonic_exchange.status", status).getString(), statusColor));
        lines.add(new InfoPanel.Line(
            Component.translatable("gui.hexwright.harmonic_exchange.subscribers", snapshot.totalSubscribers())
                .getString(), TEXT_COLOR));

        boolean anyChannel = false;
        for (int harmonic = 0; harmonic < HarmonicExchangeState.HARMONIC_COUNT; harmonic++) {
            int count = snapshot.counts()[harmonic];
            boolean retained = snapshot.retained()[harmonic];
            if (count == 0 && !retained) {
                continue;
            }
            if (!anyChannel) {
                lines.add(new InfoPanel.Line("", TEXT_COLOR));
                anyChannel = true;
            }
            String key = retained
                ? "gui.hexwright.harmonic_exchange.harmonic.retained"
                : "gui.hexwright.harmonic_exchange.harmonic";
            lines.add(new InfoPanel.Line(Component.translatable(key, harmonic, count).getString(),
                count == 0 ? DIM_COLOR : TEXT_COLOR));
        }
        if (!anyChannel) {
            lines.add(new InfoPanel.Line("", TEXT_COLOR));
            lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_exchange.silent")
                .withStyle(ChatFormatting.ITALIC).getString(), DIM_COLOR));
        }

        lines.add(new InfoPanel.Line("", TEXT_COLOR));
        lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_exchange.transmissions").getString(),
            HEADING_COLOR));
        lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.harmonic_exchange.last_minute",
            snapshot.transmissionsLastMinute()).getString(), TEXT_COLOR));
        return lines;
    }

}
