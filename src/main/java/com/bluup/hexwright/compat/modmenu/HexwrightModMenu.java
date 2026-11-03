package com.bluup.hexwright.compat.modmenu;

import com.bluup.hexwright.client.settings.HexwrightSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class HexwrightModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HexwrightSettingsScreen::new;
    }
}
