package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.Hexwright;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class EmissiveItemModels {
    private static final long QUAD_SEED = 42L;

    private static final Direction[] DIRECTIONS = Direction.values();

    private EmissiveItemModels() {
    }

    public static void register() {
        PreparableModelLoadingPlugin.register(
            (resources, executor) -> CompletableFuture.supplyAsync(
                () -> EmissiveModelScan.scan(resources), executor),
            EmissiveItemModels::onModelLoad);
    }

    private static void onModelLoad(EmissiveModelScan scan, ModelLoadingPlugin.Context context) {
        if (scan.isEmpty()) {
            return;
        }

        context.addModels(scan.baseByGlowId().keySet());
        context.addModels(scan.partTwinJson().keySet());

        context.resolveModel().register(resolution -> {
            String partJson = scan.partTwinJson().get(resolution.id());
            if (partJson != null) {
                return partTwinOf(resolution.id(), partJson);
            }
            ResourceLocation base = scan.baseByGlowId().get(resolution.id());
            if (base == null) {
                return null;
            }
            return twinOf(base, scan.texturesByGlowId().get(resolution.id()));
        });

        context.modifyModelAfterBake().register((model, baked) -> {
            if (model == null) {
                return null;
            }
            ResourceLocation fileId = modelFileId(baked.id());
            EmissiveBakedModel wrapped = wrap(model, fileId, scan);
            if (wrapped == null) {
                return model;
            }
            Transformation rotation = baked.settings().getRotation();
            if (!rotation.equals(Transformation.identity())) {
                wrapped.modelRotation(rotation);
            }
            return wrapped;
        });
    }

    @Nullable
    private static EmissiveBakedModel wrap(BakedModel model, ResourceLocation fileId,
                                           EmissiveModelScan scan) {
        ResourceLocation glow = scan.glowByBaseId().get(fileId);
        if (glow != null) {
            return new EmissiveBakedModel(model, glow);
        }
        GlowParams params = scan.brightnessSources().get(fileId);
        if (params == null) {
            return null;
        }
        PartTwin twin = scan.partTwinByBase().get(fileId);
        return twin == null
            ? new EmissiveBakedModel(model, params)
            : new EmissiveBakedModel(model, twin, params);
    }

    private static BlockModel twinOf(ResourceLocation base, Map<String, String> textures) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", base.toString());
        JsonObject declared = new JsonObject();
        textures.forEach(declared::addProperty);
        root.add("textures", declared);
        try {
            return BlockModel.fromString(root.toString());
        } catch (RuntimeException e) {
            Hexwright.LOGGER.error("Could not build the glowmask twin of {}", base, e);
            return null;
        }
    }

    private static BlockModel partTwinOf(ResourceLocation twinId, String json) {
        try {
            return BlockModel.fromString(json);
        } catch (RuntimeException e) {
            Hexwright.LOGGER.error("Could not build the glowing-parts twin {}", twinId, e);
            return null;
        }
    }

    private static ResourceLocation modelFileId(ResourceLocation id) {
        if (id instanceof ModelResourceLocation model && model.getVariant().equals("inventory")) {
            return new ResourceLocation(model.getNamespace(), "item/" + model.getPath());
        }
        return id;
    }

    public static void renderGlow(BakedModel model, PoseStack poseStack,
                                  MultiBufferSource buffers, int overlay, RenderType itemLayer,
                                  boolean handPose) {
        if (!(model instanceof EmissiveBakedModel emissive)) {
            return;
        }
        BakedModel glow = emissive.hasTwin() ? emissive.glowModel() : emissive;
        RenderType layer = emissive.thresholded()
            ? EmissiveGlowLayer.layer()
            : EmissiveGlowLayer.maskedLayer();
        if (glow == null || layer == null) {
            return;
        }

        MultiBufferSource.BufferSource source =
            buffers instanceof MultiBufferSource.BufferSource batched ? batched : null;
        if (source != null) {
            source.endBatch(itemLayer);
        }
        PoseStack.Pose pose = poseStack.last();
        GlowParams glowParams = emissive.params();
        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        float strength = glowParams.strength();
        float red = strength;
        float green = strength;
        float blue = strength;
        if (emissive.thresholded()) {
            green = glowParams.resolveThreshold(config.glowThreshold);
            blue = glowParams.resolveSaturation(config.glowSaturation);
            EmissiveGlowLayer.updateKnee(config.glowKnee);
        }
        emitGlowQuads(buffers.getBuffer(layer), pose, glow, overlay, red, green, blue);
        if (source != null) {
            source.endBatch(layer);
        }

        EmissiveBloom.captureGlow(pose, glow, layer, overlay, red, green, blue, handPose);
    }

    static void emitGlowQuads(VertexConsumer consumer, PoseStack.Pose pose, BakedModel glow,
                              int overlay, float red, float green, float blue) {
        RandomSource random = RandomSource.create();
        for (Direction direction : DIRECTIONS) {
            random.setSeed(QUAD_SEED);
            emit(consumer, pose, glow.getQuads(null, direction, random), overlay, red, green, blue);
        }
        random.setSeed(QUAD_SEED);
        emit(consumer, pose, glow.getQuads(null, null, random), overlay, red, green, blue);
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, List<BakedQuad> quads,
                             int overlay, float red, float green, float blue) {
        for (BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, red, green, blue, LightTexture.FULL_BRIGHT, overlay);
        }
    }
}
