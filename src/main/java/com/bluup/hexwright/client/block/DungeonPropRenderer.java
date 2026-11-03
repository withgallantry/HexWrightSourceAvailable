package com.bluup.hexwright.client.block;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.DungeonProp;
import com.bluup.hexwright.server.block.DungeonPropBlockEntity;
import com.bluup.hexwright.server.block.DungeonPropCollision;
import com.bluup.hexwright.server.block.DungeonPropItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.EnumMap;
import java.util.Map;

public class DungeonPropRenderer extends GeoBlockRenderer<DungeonPropBlockEntity> {

    public DungeonPropRenderer(BlockEntityRendererProvider.Context context) {
        super(new PropModel());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        super.rotateBlock(facing, poseStack);
        DungeonPropBlockEntity prop = getAnimatable();
        if (prop == null) {
            return;
        }
        poseStack.translate(prop.offsetX(), prop.offsetY(), prop.offsetZ());
        if (prop.turn() != 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(prop.turn()));
        }
    }

    public static void register() {
        BlockEntityRenderers.register(DungeonProp.BLOCK_ENTITY, DungeonPropRenderer::new);
        for (DungeonProp prop : DungeonProp.values()) {
            BuiltinItemRendererRegistry.INSTANCE.register(DungeonPropItems.item(prop), new PropItem(prop));
        }
    }

    private static final class PropItem implements BuiltinItemRendererRegistry.DynamicItemRenderer {

        private final DungeonPropBlockEntity entity;
        private final float scale;
        private final float centreX;
        private final float centreY;
        private final float centreZ;

        private PropItem(DungeonProp prop) {
            this.entity = new DungeonPropBlockEntity(BlockPos.ZERO, prop.block().defaultBlockState());
            this.entity.placeByHand();
            int[] b = DungeonPropCollision.bounds(prop);
            float width = b[3] - b[0];
            float height = b[4] - b[1];
            float depth = b[5] - b[2];
            float largest = Math.max(1, Math.max(width, Math.max(depth, height)));
            this.scale = Math.min(1.5F, 16F / largest);
            this.centreX = (b[0] + b[3]) / 32F + this.entity.offsetX();
            this.centreY = (b[1] + b[4]) / 32F + this.entity.offsetY();
            this.centreZ = (b[2] + b[5]) / 32F + this.entity.offsetZ();
        }

        @Override
        public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack,
                           MultiBufferSource buffers, int light, int overlay) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(this.scale, this.scale, this.scale);
            poseStack.translate(-this.centreX, -this.centreY, -this.centreZ);
            Minecraft.getInstance().getBlockEntityRenderDispatcher()
                .renderItem(this.entity, poseStack, buffers, light, overlay);
            poseStack.popPose();
        }
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
