package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.RuinedPortalFrameBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class RuinedPortalFrameRenderer extends GeoBlockRenderer<RuinedPortalFrameBlockEntity> {

    public RuinedPortalFrameRenderer(BlockEntityRendererProvider.Context context) {
        super(new RuinedPortalFrameModel());
    }

    public static void register() {
        BlockEntityRenderers.register(
            HexwrightBlocks.RUINED_PORTAL_FRAME_BLOCK_ENTITY,
            RuinedPortalFrameRenderer::new
        );
    }
}
