package com.bluup.hexwright.client.block;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.WeaponRackBlock;
import com.bluup.hexwright.server.block.WeaponRackBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class WeaponRackRenderer extends GeoBlockRenderer<WeaponRackBlockEntity> {

    private static final float HANG = 135f;

    private static final float WEAPON_SCALE = 1.3f;

    public WeaponRackRenderer(BlockEntityRendererProvider.Context context) {
        super(new RackModel());
    }

    public static void register() {
        BlockEntityRenderers.register(HexwrightBlocks.WEAPON_RACK_BLOCK_ENTITY, WeaponRackRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(HexwrightBlocks.WEAPON_RACK_ITEM, new RackItem());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        super.rotateBlock(facing, poseStack);
        poseStack.translate(WeaponRackBlock.ANCHOR_X, 0, WeaponRackBlock.ANCHOR_Z);
        poseStack.mulPose(Axis.YP.rotationDegrees(WeaponRackBlock.TURN));
    }

    @Override
    public void postRender(PoseStack poseStack, WeaponRackBlockEntity rack, BakedGeoModel model,
                           MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                           float partialTick, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha) {
        super.postRender(poseStack, rack, model, bufferSource, buffer, isReRender, partialTick,
            packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-WeaponRackBlock.TURN));
        poseStack.translate(-WeaponRackBlock.ANCHOR_X, 0, -WeaponRackBlock.ANCHOR_Z);
        for (int slot = 0; slot < WeaponRackBlock.SLOTS; slot++) {
            ItemStack stack = rack.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(WeaponRackBlock.SLOT_X[slot], WeaponRackBlock.SLOT_Y, WeaponRackBlock.SLOT_Z);
            poseStack.mulPose(Axis.ZP.rotationDegrees(HANG));
            poseStack.scale(WEAPON_SCALE, WEAPON_SCALE, WEAPON_SCALE);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, bufferSource, rack.getLevel(),
                (int) rack.getBlockPos().asLong() + slot);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static final class RackItem implements BuiltinItemRendererRegistry.DynamicItemRenderer {

        private final WeaponRackBlockEntity entity = new WeaponRackBlockEntity(
            BlockPos.ZERO, HexwrightBlocks.WEAPON_RACK_BLOCK.defaultBlockState());

        private final float scale = 1f / WeaponRackBlock.MODEL_WIDTH;

        @Override
        public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack,
                           MultiBufferSource buffers, int light, int overlay) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.scale(this.scale, this.scale, this.scale);
            poseStack.translate(-0.5f, -WeaponRackBlock.MODEL_CENTRE_Y,
                -(0.5f + WeaponRackBlock.MODEL_CENTRE_Z));
            Minecraft.getInstance().getBlockEntityRenderDispatcher()
                .renderItem(this.entity, poseStack, buffers, light, overlay);
            poseStack.popPose();
        }
    }

    private static final class RackModel extends GeoModel<WeaponRackBlockEntity> {

        private static final ResourceLocation MODEL = Hexwright.id("geo/block/props/empty_rack.geo.json");
        private static final ResourceLocation TEXTURE = Hexwright.id("textures/block/props/empty_rack.png");

        @Override
        public ResourceLocation getModelResource(WeaponRackBlockEntity rack) {
            return MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(WeaponRackBlockEntity rack) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(WeaponRackBlockEntity rack) {
            return Hexwright.id("animations/armour.animation.json");
        }

        @Override
        public RenderType getRenderType(WeaponRackBlockEntity rack, ResourceLocation texture) {
            return RenderType.entityCutout(texture);
        }
    }
}
