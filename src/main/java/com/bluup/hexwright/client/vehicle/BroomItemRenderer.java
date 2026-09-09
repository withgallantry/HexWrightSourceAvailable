package com.bluup.hexwright.client.vehicle;

import com.bluup.hexwright.server.vehicle.BroomItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class BroomItemRenderer extends GeoItemRenderer<BroomItem> {

    public BroomItemRenderer() {
        super(new BroomGeoModel());
    }
}
