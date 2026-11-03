package com.bluup.hexwright.client.block;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.RuinedPortalFrameBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

public class RuinedPortalFrameModel extends DefaultedBlockGeoModel<RuinedPortalFrameBlockEntity> {

    public RuinedPortalFrameModel() {
        super(Hexwright.id("ruined_portal_frame"));
    }

    @Override
    public RenderType getRenderType(RuinedPortalFrameBlockEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
