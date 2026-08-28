package com.bluup.hexwright.client.vehicle;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.vehicle.CarpetEntity;
import com.bluup.hexwright.server.vehicle.CarpetVariant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CarpetTailModels {

    private CarpetTailModels() {
    }

    private static final String[] PART_NAMES = {"base", "tail1", "tail2", "tail3"};
    private static final int TAIL_SEGMENTS = PART_NAMES.length - 1;

    private static final float PIVOT_Y = -0.609375f;
    private static final float[] PIVOT_Z = {0.419194f, 0.637944f, 0.856694f};

    private static final float[] AMPLITUDE_DEGREES = {7.5f, 12.5f, 17.5f};

    private static final float FLIGHT_AMPLITUDE_SCALE = 1.35f;

    private static final float SEGMENT_PHASE_LAG_DEGREES = 60.0f;

    private static final float PHASE_SPREAD_DEGREES = 3.6f;

    private static final Map<String, ResourceLocation[]> PARTS = buildPartIds();

    private static Map<String, ResourceLocation[]> buildPartIds() {
        Map<String, ResourceLocation[]> parts = new HashMap<>();
        for (CarpetVariant variant : CarpetVariant.values()) {
            ResourceLocation[] ids = new ResourceLocation[PART_NAMES.length];
            for (int i = 0; i < PART_NAMES.length; i++) {
                ids[i] = Hexwright.id("entity/carpet_" + variant.id() + "_" + PART_NAMES[i]);
            }
            parts.put(variant.id(), ids);
        }
        return parts;
    }

    public static void registerModels() {
        List<ResourceLocation> all = new ArrayList<>();
        for (ResourceLocation[] ids : PARTS.values()) {
            all.addAll(List.of(ids));
        }
        ModelLoadingPlugin.register(context -> context.addModels(all));
    }

    public static boolean render(
        CarpetEntity entity,
        ItemStack displayStack,
        float partialTicks,
        ItemRenderer itemRenderer,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight
    ) {
        BakedModel[] models = bakedParts(entity.getVariant());
        if (models == null) {
            return false;
        }

        float phase = entity.getRipplePhase(partialTicks) + (entity.getId() % 100) * PHASE_SPREAD_DEGREES;
        float amplitudeScale = Mth.lerp(entity.getRippleFlow(partialTicks), 1.0f, FLIGHT_AMPLITUDE_SCALE);

        drawPart(models[0], displayStack, itemRenderer, poseStack, buffer, packedLight);

        poseStack.pushPose();
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            float angle = Mth.sin((phase - i * SEGMENT_PHASE_LAG_DEGREES) * Mth.DEG_TO_RAD)
                * AMPLITUDE_DEGREES[i] * amplitudeScale;
            poseStack.translate(0.0f, PIVOT_Y, PIVOT_Z[i]);
            poseStack.mulPose(Axis.XP.rotationDegrees(angle));
            poseStack.translate(0.0f, -PIVOT_Y, -PIVOT_Z[i]);
            drawPart(models[i + 1], displayStack, itemRenderer, poseStack, buffer, packedLight);
        }
        poseStack.popPose();
        return true;
    }

    private static void drawPart(
        BakedModel model,
        ItemStack displayStack,
        ItemRenderer itemRenderer,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight
    ) {
        itemRenderer.render(
            displayStack, ItemDisplayContext.NONE, false,
            poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, model
        );
    }

    private static BakedModel @Nullable [] bakedParts(String variantId) {
        ResourceLocation[] ids = PARTS.get(CarpetVariant.byId(variantId).id());
        if (ids == null) {
            return null;
        }
        FabricBakedModelManager manager = (FabricBakedModelManager) Minecraft.getInstance().getModelManager();
        BakedModel[] models = new BakedModel[ids.length];
        for (int i = 0; i < ids.length; i++) {
            models[i] = manager.getModel(ids[i]);
            if (models[i] == null) {
                return null;
            }
        }
        return models;
    }
}
