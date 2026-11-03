package com.bluup.hexwright.server.malediction.workbench;

import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@LDLRegister(name = "malediction_forms", group = MaledictionWorkbenchWidget.EDITOR_GROUP)
public class FormPickerWidget extends MaledictionPickerWidget<MaledictionForm> {

    private static final List<MaledictionForm> FORMS = List.of(MaledictionForm.values());

    public FormPickerWidget() {
        super(176, 44);
    }

    @Override
    protected List<MaledictionForm> choices() {
        return FORMS;
    }

    @Override
    protected @Nullable MaledictionForm picked() {
        MaledictionSession session = session();
        return session == null ? null : session.form();
    }

    @Override
    protected void pick(MaledictionForm form) {
        MaledictionSession session = session();
        if (session != null) {
            session.pick(form);
        }
    }

    @Override
    protected boolean known(MaledictionForm form) {
        MaledictionSession session = session();
        return session == null || session.knows(form);
    }

    @Override
    protected ItemStack icon(MaledictionForm form) {
        return new ItemStack(HexwrightItems.MALEDICTION_FORMS.get(form));
    }

    @Override
    protected List<Component> tooltip(MaledictionForm form) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(form.nameKey()).withStyle(form.rarity().color));
        lines.add(Component.translatable("item.hexwright." + form.itemId() + ".desc").withStyle(ChatFormatting.GRAY));
        if (!known(form)) {
            lines.add(Component.translatable("gui.hexwright.malediction_workbench.locked",
                Component.translatable("item.hexwright." + form.itemId())).withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}
