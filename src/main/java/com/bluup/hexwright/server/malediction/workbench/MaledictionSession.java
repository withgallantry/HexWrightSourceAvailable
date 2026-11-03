package com.bluup.hexwright.server.malediction.workbench;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class MaledictionSession extends Widget {

    private static final int UPDATE_KNOWN = 1;
    private static final int ACTION_FUSE = 1;
    private static final int FINISH_SHIFT = 32;

    private final MaledictionWorkbenchBlockEntity workbench;
    private @Nullable MaledictionForm form;
    private @Nullable MaledictionFinish finish;
    private long known;
    private long sentMask = -1L;

    MaledictionSession(MaledictionWorkbenchBlockEntity workbench) {
        super(0, 0, 0, 0);
        this.workbench = workbench;
    }

    public MaledictionWorkbenchBlockEntity workbench() {
        return workbench;
    }

    public @Nullable MaledictionForm form() {
        return form;
    }

    public @Nullable MaledictionFinish finish() {
        return finish;
    }

    public void pick(MaledictionForm form) {
        this.form = form;
    }

    public void pick(MaledictionFinish finish) {
        this.finish = finish;
    }

    public boolean knows(MaledictionForm form) {
        return (known & 1L << form.ordinal()) != 0;
    }

    public boolean knows(MaledictionFinish finish) {
        return (known & 1L << (FINISH_SHIFT + finish.ordinal())) != 0;
    }

    public boolean canFuse() {
        return workbench.hasBlank() && form != null && finish != null && knows(form) && knows(finish)
            && !workbench.holds(form, finish);
    }

    @Environment(EnvType.CLIENT)
    public void fuse() {
        if (form != null && finish != null) {
            MaledictionForm pickedForm = form;
            MaledictionFinish pickedFinish = finish;
            writeClientAction(ACTION_FUSE, buf -> {
                buf.writeVarInt(pickedForm.ordinal());
                buf.writeVarInt(pickedFinish.ordinal());
            });
        }
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id != ACTION_FUSE || gui == null || !(gui.entityPlayer instanceof ServerPlayer player)) {
            super.handleClientAction(id, buffer);
            return;
        }
        int formIndex = buffer.readVarInt();
        int finishIndex = buffer.readVarInt();
        if (formIndex < 0 || formIndex >= MaledictionForm.values().length
            || finishIndex < 0 || finishIndex >= MaledictionFinish.values().length) {
            return;
        }
        Component refusal = workbench.fuse(player, MaledictionForm.values()[formIndex],
            MaledictionFinish.values()[finishIndex]);
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
        known = buffer.readVarLong();
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
            known = buffer.readVarLong();
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    private long mask() {
        if (gui == null || !(gui.entityPlayer instanceof ServerPlayer player)) {
            return 0L;
        }
        long mask = 0L;
        for (MaledictionForm candidate : MaledictionForm.values()) {
            if (MaledictionWorkbenchBlockEntity.knows(player, candidate)) {
                mask |= 1L << candidate.ordinal();
            }
        }
        for (MaledictionFinish candidate : MaledictionFinish.values()) {
            if (MaledictionWorkbenchBlockEntity.knows(player, candidate)) {
                mask |= 1L << (FINISH_SHIFT + candidate.ordinal());
            }
        }
        return mask;
    }
}
