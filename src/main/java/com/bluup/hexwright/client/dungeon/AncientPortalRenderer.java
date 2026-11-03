package com.bluup.hexwright.client.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.dungeon.AncientAltarBlock;
import com.bluup.hexwright.server.dungeon.AncientAltarBlockEntity;
import com.bluup.hexwright.server.dungeon.AncientPortalBlock;
import com.bluup.hexwright.server.dungeon.AncientPortalBlockEntity;
import com.bluup.hexwright.server.dungeon.AncientPortalVariant;
import com.bluup.hexwright.server.dungeon.AncientPortals;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.EnumMap;
import java.util.Map;

public class AncientPortalRenderer extends GeoBlockRenderer<AncientPortalBlockEntity> {

    private static final int VIEW_DISTANCE = 128;

    private static final Map<AncientPortalVariant, ResourceLocation> PORTAL_TEXTURES =
        textures("textures/block/ancient_portal_");
    private static final Map<AncientPortalVariant, ResourceLocation> ALTAR_TEXTURES =
        textures("textures/block/ancient_altar_");

    public AncientPortalRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<AncientPortalBlockEntity>(Hexwright.id("ancient_portal")) {
            @Override
            public ResourceLocation getTextureResource(AncientPortalBlockEntity portal) {
                return PORTAL_TEXTURES.get(portal.variant());
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public boolean shouldRenderOffScreen(AncientPortalBlockEntity portal) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    private static CrystalAltarRenderer<AncientAltarBlockEntity> altar() {
        return new CrystalAltarRenderer<>(new DefaultedBlockGeoModel<AncientAltarBlockEntity>(Hexwright.id("ancient_altar")) {
            @Override
            public ResourceLocation getTextureResource(AncientAltarBlockEntity altar) {
                return ALTAR_TEXTURES.get(altar.getBlockState().getBlock() instanceof AncientAltarBlock block
                    ? block.variant()
                    : AncientPortalVariant.STONE);
            }
        });
    }

    private static Map<AncientPortalVariant, ResourceLocation> textures(String prefix) {
        Map<AncientPortalVariant, ResourceLocation> textures = new EnumMap<>(AncientPortalVariant.class);
        for (AncientPortalVariant variant : AncientPortalVariant.values()) {
            textures.put(variant, Hexwright.id(prefix + variant.id() + ".png"));
        }
        return textures;
    }

    static void register() {
        BlockEntityRenderers.register(AncientPortals.PORTAL_BLOCK_ENTITY, AncientPortalRenderer::new);
        BlockEntityRenderers.register(AncientPortals.ALTAR_BLOCK_ENTITY, context -> altar());
        for (AncientPortalVariant variant : AncientPortalVariant.values()) {
            AncientPortalBlock block = variant.portal();
            BlockState state = block.defaultBlockState();
            BuiltinItemRendererRegistry.INSTANCE.register(variant.portalItem(),
                new TravelStoneRenderer.Icon(new AncientPortalBlockEntity(BlockPos.ZERO, state), 0.11F, 3.4F));
        }
    }
}
