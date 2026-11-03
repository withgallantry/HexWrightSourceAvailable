package com.bluup.hexwright.client.armour;

import com.bluup.hexwright.server.armour.HexwrightArmourItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class HexwrightArmourRenderer extends GeoArmorRenderer<HexwrightArmourItem> {

    public HexwrightArmourRenderer() {
        super(new HexwrightArmourModel());
    }
}
