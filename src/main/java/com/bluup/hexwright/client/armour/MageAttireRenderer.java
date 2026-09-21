package com.bluup.hexwright.client.armour;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public final class MageAttireRenderer {

    public static final class Placement {
        private final float defaultX;
        private final float defaultY;
        private final float defaultZ;
        private final float defaultScale;

        private float x;
        private float y;
        private float z;
        private float scale;
        private float yaw = 180.0f;

        Placement(float defaultX, float defaultY, float defaultZ, float defaultScale) {
            this.defaultX = defaultX;
            this.defaultY = defaultY;
            this.defaultZ = defaultZ;
            this.defaultScale = defaultScale;
            reset();
        }

        public void reset() {
            x = defaultX;
            y = defaultY;
            z = defaultZ;
            scale = defaultScale;
            yaw = 180.0f;
        }

        public void setOffset(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void setScale(float value) {
            this.scale = value;
        }

        public void setYaw(float value) {
            this.yaw = value;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float z() {
            return z;
        }

        public float scale() {
            return scale;
        }

        public float yaw() {
            return yaw;
        }
    }

    public static final Placement MANTLE = new Placement(0.0f, 0.25f, 0.1f, 1.3f);

    public static final Placement HAT = new Placement(0.0f, 0.9f, 0.0f, 1.0f);

    public static final Placement CAPE = new Placement(0.0f, -0.89f, 0.05f, 1.25f);

    public static final Placement PASSAGE_HAT = new Placement(0.0f, 0.875f, 0.0f, 1.25f);

    private MageAttireRenderer() {
    }

    public static void register() {
        ArmorRenderer.register(MageAttireRenderer::renderMantle, HexwrightItems.MANTLE_OF_ASCENSION);
        ArmorRenderer.register(MageAttireRenderer::renderHat, HexwrightItems.HAT_OF_ASCENSION);
        ArmorRenderer.register((pose, buffers, stack, entity, slot, light, model) ->
            draw(pose, buffers, stack, entity, light, model.body, CAPE), HexwrightItems.CAPE_OF_PASSAGE);
        ArmorRenderer.register((pose, buffers, stack, entity, slot, light, model) ->
            draw(pose, buffers, stack, entity, light, model.head, PASSAGE_HAT), HexwrightItems.HAT_OF_PASSAGE);
    }

    private static void renderMantle(PoseStack pose, MultiBufferSource buffers, ItemStack stack,
                                     LivingEntity entity, EquipmentSlot slot, int light,
                                     HumanoidModel<LivingEntity> model) {
        draw(pose, buffers, stack, entity, light, model.body, MANTLE);
    }

    private static void renderHat(PoseStack pose, MultiBufferSource buffers, ItemStack stack,
                                  LivingEntity entity, EquipmentSlot slot, int light,
                                  HumanoidModel<LivingEntity> model) {
        draw(pose, buffers, stack, entity, light, model.head, HAT);
    }

    private static void draw(PoseStack pose, MultiBufferSource buffers, ItemStack stack,
                             LivingEntity wearer, int light, ModelPart anchor, Placement where) {
        pose.pushPose();
        anchor.translateAndRotate(pose);
        pose.mulPose(Axis.XP.rotationDegrees(180.0f));
        pose.mulPose(Axis.YP.rotationDegrees(where.yaw()));
        pose.translate(where.x(), where.y(), where.z());
        pose.scale(where.scale(), where.scale(), where.scale());
        Minecraft.getInstance().getItemRenderer().renderStatic(
            stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers,
            wearer.level(), 0);
        pose.popPose();
    }
}
