package com.bluup.hexwright.client.block;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.DungeonProp;
import com.bluup.hexwright.server.block.DungeonPropBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.EnumMap;
import java.util.Map;

public class DungeonPropRenderer extends GeoBlockRenderer<DungeonPropBlockEntity> {

    public DungeonPropRenderer(BlockEntityRendererProvider.Context context) {
        super(new PropModel());
    }

    public static void register() {
        BlockEntityRenderers.register(DungeonProp.BLOCK_ENTITY, DungeonPropRenderer::new);
    }

    private static final class PropModel extends GeoModel<DungeonPropBlockEntity> {

        private final Map<DungeonProp, ResourceLocation> models = new EnumMap<>(DungeonProp.class);
        private final Map<DungeonProp, ResourceLocation> textures = new EnumMap<>(DungeonProp.class);

        private PropModel() {
            for (DungeonProp prop : DungeonProp.values()) {
                this.models.put(prop, Hexwright.id("geo/block/props/" + prop.propName() + ".geo.json"));
                this.textures.put(prop, Hexwright.id("textures/block/props/" + prop.propName() + ".png"));
            }
        }

        @Override
        public ResourceLocation getModelResource(DungeonPropBlockEntity prop) {
            return this.models.get(prop.prop());
        }

        @Override
        public ResourceLocation getTextureResource(DungeonPropBlockEntity prop) {
            return this.textures.get(prop.prop());
        }

        @Override
        public ResourceLocation getAnimationResource(DungeonPropBlockEntity prop) {
            return Hexwright.id("animations/armour.animation.json");
        }

        @Override
        public RenderType getRenderType(DungeonPropBlockEntity prop, ResourceLocation texture) {
            return RenderType.entityCutout(texture);
        }
    }
}
