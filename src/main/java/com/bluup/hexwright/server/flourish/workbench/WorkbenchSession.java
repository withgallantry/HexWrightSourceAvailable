package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.server.flourish.FlourishBlankItem;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class WorkbenchSession extends Widget {

    private static final int UPDATE_KNOWN = 1;
    private static final int ACTION_INSCRIBE = 1;

    private final FlourishWorkbenchBlockEntity workbench;
    private final FlourishDraft draft = new FlourishDraft();
    private final Set<Flourish> known = EnumSet.noneOf(Flourish.class);

    private long sentMask = -1L;
    @Nullable
    private FlourishDesign seen;

    WorkbenchSession(FlourishWorkbenchBlockEntity workbench) {
        super(0, 0, 0, 0);
        this.workbench = workbench;
    }

    public FlourishWorkbenchBlockEntity workbench() {
        return workbench;
    }

    public FlourishDraft draft() {
        return draft;
    }

    public boolean known(Flourish flourish) {
        return flourish.rarity() == Rarity.UNCOMMON || known.contains(flourish);
    }

    public @Nullable Flourish missing() {
        for (Flourish source : draft.design().sources()) {
            if (!known(source)) {
                return source;
            }
        }
        return null;
    }

    public boolean inscribed() {
        return draft.design().equals(FlourishBlankItem.design(workbench.blank()));
    }

    public boolean canInscribe() {
        return workbench.hasBlank() && missing() == null && !inscribed();
    }

    @Environment(EnvType.CLIENT)
    public void inscribe() {
        FlourishDesign design = draft.design();
        writeClientAction(ACTION_INSCRIBE, design::write);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id != ACTION_INSCRIBE || gui == null || !(gui.entityPlayer instanceof ServerPlayer player)) {
            super.handleClientAction(id, buffer);
            return;
        }
        FlourishDesign design;
        try {
            design = FlourishDesign.read(buffer);
        } catch (RuntimeException e) {
            Hexwright.LOGGER.warn("Ignoring a malformed flourish design from {}: {}", player.getGameProfile().getName(),
                e.toString());
            return;
        }
        Component refusal = workbench.inscribe(player, design);
        if (refusal != null) {
            player.displayClientMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        sentMask = mask();
        buffer.writeVarLong(sentMask);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        unmask(buffer.readVarLong());
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        long mask = mask();
        if (mask != sentMask) {
            sentMask = mask;
            writeUpdateInfo(UPDATE_KNOWN, buf -> buf.writeVarLong(mask));
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == UPDATE_KNOWN) {
            unmask(buffer.readVarLong());
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        FlourishDesign inSlot = FlourishBlankItem.design(workbench.blank());
        if (!Objects.equals(inSlot, seen)) {
            seen = inSlot;
            if (inSlot != null) {
                draft.load(inSlot);
            }
        }
    }

    private long mask() {
        if (gui == null || !(gui.entityPlayer instanceof ServerPlayer player)) {
            return 0;
        }
        long mask = 0L;
        for (Flourish flourish : FlourishWorkbenchBlockEntity.known(player)) {
            mask |= 1L << flourish.ordinal();
        }
        return mask;
    }

    private void unmask(long mask) {
        known.clear();
        for (Flourish flourish : Flourish.values()) {
            if ((mask & 1L << flourish.ordinal()) != 0) {
                known.add(flourish);
            }
        }
    }
}
