package com.bluup.hexwright.client.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.dungeon.CrystalAltarBlockEntity;
import com.bluup.hexwright.server.dungeon.DungeonDimension;
import com.bluup.hexwright.server.dungeon.DungeonInstances;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class CrystalAltarRenderer extends GeoBlockRenderer<CrystalAltarBlockEntity> {

    private static final float KEY_SCALE = 0.75F;
    private static final double KEY_BOB = 0.06;
    private static final float KEY_BOB_TICKS = 40.0F;
    private static final float KEY_DEGREES_PER_TICK = 3.0F;

    private static final double TEXT_HEIGHT = 2.35;
    private static final float TEXT_SCALE = 0.035F;
    private static final int TEXT_COLOUR = 0xFFE0C8FF;
    private static final int BACKGROUND = 0x50000000;

    public CrystalAltarRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(Hexwright.id("crystal_altar")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void postRender(PoseStack poseStack, CrystalAltarBlockEntity altar, BakedGeoModel model,
                           MultiBufferSource buffers, VertexConsumer buffer, boolean isReRender, float partialTick,
                           int light, int overlay, float red, float green, float blue, float alpha) {
        super.postRender(poseStack, altar, model, buffers, buffer, isReRender, partialTick, light, overlay,
            red, green, blue, alpha);
        if (isReRender || !altar.lit() || altar.getLevel() == null) {
            return;
        }
        renderKey(poseStack, altar, buffers, partialTick, overlay);
        Component text = label(altar);
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(0.0, TEXT_HEIGHT, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, TEXT_COLOUR, false, poseStack.last().pose(), buffers,
            Font.DisplayMode.NORMAL, BACKGROUND, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static Component label(CrystalAltarBlockEntity altar) {
        if (DungeonDimension.isDungeonLevel(altar.getLevel())) {
            return Component.translatable("block.hexwright.crystal_altar.close");
        }
        if (altar.closesAt() == 0L) {
            return Component.translatable("block.hexwright.crystal_altar.opening");
        }
        return Component.literal(DungeonInstances.duration(
            Math.max(0L, altar.closesAt() - altar.getLevel().getGameTime())));
    }

    private static void renderKey(PoseStack poseStack, CrystalAltarBlockEntity altar, MultiBufferSource buffers,
                                  float partialTick, int overlay) {
        Level level = altar.getLevel();
        float time = (level.getGameTime() % 72000L) + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0, CrystalAltarBlockEntity.KEY_HEIGHT + KEY_BOB * Mth.sin(time * Mth.TWO_PI / KEY_BOB_TICKS), 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * KEY_DEGREES_PER_TICK));
        poseStack.scale(KEY_SCALE, KEY_SCALE, KEY_SCALE);
        int light = LevelRenderer.getLightColor(level, altar.getBlockPos().above());
        Minecraft.getInstance().getItemRenderer().renderStatic(altar.key(), ItemDisplayContext.FIXED, light, overlay,
            poseStack, buffers, level, (int) altar.getBlockPos().asLong());
        poseStack.popPose();
    }
}
