package com.bluup.hexwright.client.render.emissive;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class EmissiveBakedModel implements BakedModel {
    private final BakedModel delegate;
    private final @Nullable ResourceLocation glowModelId;
    private final GlowParams params;
    private boolean thresholded;
    private @Nullable Transformation modelRotation;

    private @Nullable BakedModel glow;

    public EmissiveBakedModel(BakedModel delegate, ResourceLocation glowModelId) {
        this(delegate, glowModelId, GlowParams.DEFAULT);
        this.thresholded = false;
    }

    public EmissiveBakedModel(BakedModel delegate, GlowParams params) {
        this(delegate, (ResourceLocation) null, params);
        this.thresholded = true;
    }

    public EmissiveBakedModel(BakedModel delegate, PartTwin twin, GlowParams params) {
        this(delegate, twin.twinId(), params);
        this.thresholded = twin.thresholded();
    }

    private EmissiveBakedModel(BakedModel delegate, @Nullable ResourceLocation glowModelId,
                               GlowParams params) {
        this.delegate = delegate;
        this.glowModelId = glowModelId;
        this.params = params;
    }

    public @Nullable Transformation modelRotation() {
        return modelRotation;
    }

    void modelRotation(@Nullable Transformation rotation) {
        this.modelRotation = rotation;
    }

    public boolean hasTwin() {
        return glowModelId != null;
    }

    @Nullable
    ResourceLocation glowModelId() {
        return glowModelId;
    }

    public boolean thresholded() {
        return thresholded;
    }

    public GlowParams params() {
        return params;
    }

    public @Nullable BakedModel glowModel() {
        if (glowModelId == null) {
            return null;
        }
        if (glow == null) {
            ModelManager manager = Minecraft.getInstance().getModelManager();
            BakedModel baked = ((FabricBakedModelManager) manager).getModel(glowModelId);
            glow = baked == manager.getMissingModel() ? null : baked;
        }
        return glow;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        return delegate.getQuads(state, face, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return delegate.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return delegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return delegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return delegate.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return delegate.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return delegate.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return delegate.getOverrides();
    }
}
