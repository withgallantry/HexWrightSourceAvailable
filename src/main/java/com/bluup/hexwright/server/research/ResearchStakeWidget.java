package com.bluup.hexwright.server.research;

import com.bluup.hexwright.client.inspiration.ClientInspiration;
import com.bluup.hexwright.server.inspiration.InspirationLevels;
import com.bluup.hexwright.server.menu.SliderFillTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SliderWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;

final class ResearchStakeWidget extends WidgetGroup {

    private static final int ACTION_SELECT = 2;
    private static final int CAP_FRACTION = 20;
    private static final int INSET_Y = 2;

    private final ResearchTableBlockEntity table;
    private final SliderWidget slider;

    ResearchStakeWidget(ResearchTableBlockEntity table, int x, int y, int width, int height, IGuiTexture frame) {
        super(x, y, width, height);
        this.table = table;
        int insetX = width / CAP_FRACTION;
        slider = new SliderWidget(insetX, INSET_Y, width - insetX * 2, height - INSET_Y * 2);
        slider.setMinAmount(0);
        slider.setMaxAmount(InspirationLevels.MAX_COMMIT);
        slider.setValueStep(InspirationLevels.MAX_COMMIT);
        slider.setHandleTexture(slider.defaultSliderHandel);
        slider.setHandleHoverTexture(slider.defaultSliderHandelHover);
        slider.setClientSideWidget();
        addWidget(slider);
        addWidget(new ImageWidget(0, 0, width, height, frame));
        if (table.getLevel() != null && table.getLevel().isClientSide) {
            dress();
        }
    }

    @Environment(EnvType.CLIENT)
    private void dress() {
        slider.setBackground(new SliderFillTexture(slider::getSliderValue, this::tint));
        slider.setSliderCallback(value -> chooseStake());
        slider.setAmount(table.session().selected());
    }

    @Environment(EnvType.CLIENT)
    private void chooseStake() {
        ResearchSession session = table.session();
        int levels = session.canSelect() ? Math.max(1, Math.round(slider.getAmount())) : session.selected();
        slider.setAmount(levels);
        if (levels != session.selected()) {
            session.select(levels);
            writeClientAction(ACTION_SELECT, buf -> buf.writeByte(levels));
        }
    }

    @Environment(EnvType.CLIENT)
    private int tint() {
        ResearchSession session = table.session();
        if (!session.canSelect()) {
            return ResearchArt.LOCKED;
        }
        return ClientInspiration.levels() >= session.stakeLevels() ? ResearchArt.LIT : ResearchArt.SHORT;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == ACTION_SELECT) {
            table.select(buffer.readByte());
        } else {
            super.handleClientAction(id, buffer);
        }
    }

    @Override
    public boolean isVisible() {
        return super.isVisible() && !table.session().isRunning();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void updateScreen() {
        slider.setAmount(table.session().selected());
        super.updateScreen();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return ResearchTrayWidget.carried(gui) == null && super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
