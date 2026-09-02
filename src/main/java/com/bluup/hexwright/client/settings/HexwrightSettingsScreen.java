package com.bluup.hexwright.client.settings;

import com.bluup.hexwright.client.portal.PortalOptions;
import com.bluup.hexwright.client.render.emissive.EmissiveBloomOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class HexwrightSettingsScreen extends OptionsSubScreen {

    private OptionsList list;

    public HexwrightSettingsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("options.hexwright.title"));
    }

    @Override
    protected void init() {
        this.list = new OptionsList(this.minecraft, this.width, this.height, 32, this.height - 32, 25);
        this.list.addSmall(new OptionInstance<?>[]{
            PortalOptions.portalViewsOption(),
            EmissiveBloomOptions.bloomOption(),
            EmissiveBloomOptions.lampGlowOption(),
        });
        this.addWidget(this.list);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
            .bounds(this.width / 2 - 100, this.height - 27, 200, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.basicListRender(graphics, this.list, mouseX, mouseY, partialTick);
    }
}
