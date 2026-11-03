package com.bluup.hexwright.server.harmonic;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ExchangeBridgeInfoWidget extends Widget {

    private static final int UPDATE_ID = 1;

    private static final int TEXT_COLOR = 0xFFC8D7FF;
    private static final int DIM_COLOR = 0xFF8895AA;
    private static final int ACTIVE_COLOR = 0xFF9BD87A;
    private static final int INACTIVE_COLOR = 0xFFE0736B;

    public record Snapshot(
        int keys,
        boolean bothAttuned,
        boolean distinctNetworks,
        boolean tuned,
        boolean networkALive,
        boolean networkBLive,
        int harmonic,
        int crossingsLastMinute
    ) {
        public static Snapshot empty() {
            return new Snapshot(0, false, false, false, false, false, 0, 0);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(keys);
            buf.writeBoolean(bothAttuned);
            buf.writeBoolean(distinctNetworks);
            buf.writeBoolean(tuned);
            buf.writeBoolean(networkALive);
            buf.writeBoolean(networkBLive);
            buf.writeVarInt(harmonic);
            buf.writeVarInt(crossingsLastMinute);
        }

        public static Snapshot read(FriendlyByteBuf buf) {
            return new Snapshot(
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt()
            );
        }

        public boolean matches(Snapshot other) {
            return keys == other.keys
                && bothAttuned == other.bothAttuned
                && distinctNetworks == other.distinctNetworks
                && tuned == other.tuned
                && networkALive == other.networkALive
                && networkBLive == other.networkBLive
                && harmonic == other.harmonic
                && crossingsLastMinute == other.crossingsLastMinute;
        }
    }

    private final Supplier<Snapshot> source;

    private Snapshot snapshot = Snapshot.empty();

    public ExchangeBridgeInfoWidget(int x, int y, int width, int height, Supplier<Snapshot> source) {
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
        if (snapshot.keys() < 2) {
            status = Component.translatable("gui.hexwright.exchange_bridge.status.needs_keys");
            statusColor = DIM_COLOR;
        } else if (!snapshot.bothAttuned()) {
            status = Component.translatable("gui.hexwright.exchange_bridge.status.key_unattuned");
            statusColor = INACTIVE_COLOR;
        } else if (!snapshot.distinctNetworks()) {
            status = Component.translatable("gui.hexwright.exchange_bridge.status.same_network");
            statusColor = INACTIVE_COLOR;
        } else if (!snapshot.networkALive() || !snapshot.networkBLive()) {
            status = Component.translatable("gui.hexwright.exchange_bridge.status.no_exchange");
            statusColor = INACTIVE_COLOR;
        } else if (!snapshot.tuned()) {
            status = Component.translatable("gui.hexwright.exchange_bridge.status.untuned");
            statusColor = DIM_COLOR;
        } else {
            status = Component.translatable("gui.hexwright.exchange_bridge.status.bridging");
            statusColor = ACTIVE_COLOR;
        }
        lines.add(new InfoPanel.Line(
            Component.translatable("gui.hexwright.exchange_bridge.status", status).getString(), statusColor));

        if (snapshot.tuned()) {
            lines.add(new InfoPanel.Line(Component.translatable("gui.hexwright.exchange_bridge.channel",
                snapshot.harmonic(), snapshot.crossingsLastMinute()).getString(), TEXT_COLOR));
        } else {
            lines.add(new InfoPanel.Line(
                Component.translatable("gui.hexwright.exchange_bridge.untuned_hint").getString(), DIM_COLOR));
        }
        return lines;
    }

}
