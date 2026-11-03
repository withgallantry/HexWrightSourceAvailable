package com.bluup.hexwright.client.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.dungeon.CrystalAltarBlockEntity;
import com.bluup.hexwright.server.dungeon.InstancedDungeons;
import com.bluup.hexwright.server.dungeon.TravelStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class TravelStoneRenderer extends GeoBlockRenderer<TravelStoneBlockEntity> {

    private static final int VIEW_DISTANCE = 192;

    private static final String HALO_BONE = "portal_frame_background";
    private static final ResourceLocation HALO_TEXTURE = Hexwright.id("textures/block/travel_stone_halo.png");

    public TravelStoneRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(Hexwright.id("travel_stone")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public boolean shouldRenderOffScreen(TravelStoneBlockEntity stone) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, TravelStoneBlockEntity stone, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (bone.getName().equals(HALO_BONE)) {
            if (isReRender) {
                return;
            }
            renderType = RenderType.entityTranslucentEmissive(HALO_TEXTURE);
            buffer = bufferSource.getBuffer(renderType);
            packedLight = LightTexture.FULL_BRIGHT;
        }
        super.renderRecursively(poseStack, stone, bone, renderType, bufferSource, buffer, isReRender, partialTick,
            packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void updateAnimatedTextureFrame(TravelStoneBlockEntity stone) {
        super.updateAnimatedTextureFrame(stone);
        AnimatableTexture.setAndUpdate(HALO_TEXTURE);
    }

    public static void register() {
        BlockEntityRenderers.register(InstancedDungeons.TRAVEL_STONE_BLOCK_ENTITY, TravelStoneRenderer::new);
        BlockEntityRenderers.register(InstancedDungeons.CRYSTAL_ALTAR_BLOCK_ENTITY, context -> CrystalAltarRenderer.crystal());
        BuiltinItemRendererRegistry.INSTANCE.register(InstancedDungeons.TRAVEL_STONE.asItem(), new Icon(
            new TravelStoneBlockEntity(BlockPos.ZERO, InstancedDungeons.TRAVEL_STONE.defaultBlockState()), 0.1F, 5.5F));
        BuiltinItemRendererRegistry.INSTANCE.register(InstancedDungeons.CRYSTAL_ALTAR.asItem(), new Icon(
            new CrystalAltarBlockEntity(BlockPos.ZERO, InstancedDungeons.CRYSTAL_ALTAR.defaultBlockState()), 0.6F, 0.9F));
        AncientPortalRenderer.register();
    }

    record Icon(BlockEntity entity, float scale, float centreY) implements BuiltinItemRendererRegistry.DynamicItemRenderer {

        @Override
        public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack, MultiBufferSource buffers,
                           int light, int overlay) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(this.scale, this.scale, this.scale);
            poseStack.translate(-0.5F, -this.centreY, -0.5F);
            Minecraft.getInstance().getBlockEntityRenderDispatcher()
                .renderItem(this.entity, poseStack, buffers, light, overlay);
            poseStack.popPose();
        }
    }
}
