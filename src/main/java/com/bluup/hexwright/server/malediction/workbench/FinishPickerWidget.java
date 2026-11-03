package com.bluup.hexwright.server.malediction.workbench;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@LDLRegister(name = "malediction_finishes", group = MaledictionWorkbenchWidget.EDITOR_GROUP)
public class FinishPickerWidget extends MaledictionPickerWidget<MaledictionFinish> {

    private static final List<MaledictionFinish> FINISHES = List.of(MaledictionFinish.values());

    public FinishPickerWidget() {
        super(176, 44);
    }

    @Override
    protected List<MaledictionFinish> choices() {
        return FINISHES;
    }

    @Override
    protected @Nullable MaledictionFinish picked() {
        MaledictionSession session = session();
        return session == null ? null : session.finish();
    }

    @Override
    protected void pick(MaledictionFinish finish) {
        MaledictionSession session = session();
        if (session != null) {
            session.pick(finish);
        }
    }

    @Override
    protected boolean known(MaledictionFinish finish) {
        MaledictionSession session = session();
        return session == null || session.knows(finish);
    }

    @Override
    protected ItemStack icon(MaledictionFinish finish) {
        return new ItemStack(HexwrightItems.MALEDICTION_FINISHES.get(finish));
    }

    @Override
    protected List<Component> tooltip(MaledictionFinish finish) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(finish.nameKey()).withStyle(finish.rarity().color));
        lines.add(Component.translatable("item.hexwright." + finish.itemId() + ".desc").withStyle(ChatFormatting.GRAY));
        if (!known(finish)) {
            lines.add(Component.translatable("gui.hexwright.malediction_workbench.locked",
                Component.translatable("item.hexwright." + finish.itemId())).withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}
