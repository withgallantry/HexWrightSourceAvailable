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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class CrystalAltarRenderer<T extends CrystalAltarBlockEntity> extends GeoBlockRenderer<T> {

    private static final float KEY_SCALE = 0.75F;
    private static final double KEY_BOB = 0.06;
    private static final float KEY_BOB_TICKS = 40.0F;
    private static final float KEY_DEGREES_PER_TICK = 3.0F;

    private static final double TEXT_ABOVE_KEY = 0.9;
    private static final float TEXT_SCALE = 0.035F;
    private static final int TEXT_COLOUR = 0xFFE0C8FF;
    private static final int BACKGROUND = 0x50000000;

    public static CrystalAltarRenderer<CrystalAltarBlockEntity> crystal() {
        return new CrystalAltarRenderer<>(new DefaultedBlockGeoModel<>(Hexwright.id("crystal_altar")));
    }

    public CrystalAltarRenderer(GeoModel<T> model) {
        super(model);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void renderFinal(PoseStack poseStack, T altar, BakedGeoModel model, MultiBufferSource buffers,
                            VertexConsumer buffer, float partialTick, int light, int overlay,
                            float red, float green, float blue, float alpha) {
        super.renderFinal(poseStack, altar, model, buffers, buffer, partialTick, light, overlay, red, green, blue,
            alpha);
        if (!altar.lit() || altar.getLevel() == null) {
            return;
        }
        Vec3 key = altar.keyOffset();
        poseStack.pushPose();
        poseStack.translate(0.5 + key.x, key.y, 0.5 + key.z);
        renderKey(poseStack, altar, buffers, partialTick, overlay);
        Component text = label(altar);
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        poseStack.translate(0.0, TEXT_ABOVE_KEY, 0.0);
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
        poseStack.translate(0.0, KEY_BOB * Mth.sin(time * Mth.TWO_PI / KEY_BOB_TICKS), 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * KEY_DEGREES_PER_TICK));
        poseStack.scale(KEY_SCALE, KEY_SCALE, KEY_SCALE);
        int light = LevelRenderer.getLightColor(level,
            BlockPos.containing(Vec3.atBottomCenterOf(altar.getBlockPos()).add(altar.keyOffset())));
        Minecraft.getInstance().getItemRenderer().renderStatic(altar.key(), ItemDisplayContext.FIXED, light, overlay,
            poseStack, buffers, level, (int) altar.getBlockPos().asLong());
        poseStack.popPose();
    }
}
