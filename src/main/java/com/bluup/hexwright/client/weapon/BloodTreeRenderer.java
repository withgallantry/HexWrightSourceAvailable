package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.BloodTreeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class BloodTreeRenderer extends GeoEntityRenderer<BloodTreeEntity> {

    private static final float SHADOW_RADIUS = 1.2F;

    public BloodTreeRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<BloodTreeEntity>(Hexwright.id("blood_tree")) {
            @Override
            public void setCustomAnimations(BloodTreeEntity tree, long instanceId,
                                            AnimationState<BloodTreeEntity> state) {
                super.setCustomAnimations(tree, instanceId, state);
                float shrink = 1.0F / tree.size();
                for (int n = 0; n < BloodTreeEntity.SACK_COUNT; n++) {
                    boolean hanging = tree.hasSack(n);
                    getBone("sack_" + n).ifPresent(bone -> {
                        bone.setHidden(!hanging);
                        bone.setScaleX(bone.getScaleX() * shrink);
                        bone.setScaleY(bone.getScaleY() * shrink);
                        bone.setScaleZ(bone.getScaleZ() * shrink);
                    });
                }
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        addRenderLayer(new BloodTreeAura(this));
    }

    @Override
    public void render(BloodTreeEntity tree, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        this.shadowRadius = SHADOW_RADIUS * tree.size();
        super.render(tree, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, BloodTreeEntity tree,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight,
                                    int packedOverlay) {
        float size = tree.size();
        super.scaleModelForRender(widthScale * size, heightScale * size, poseStack, tree, model, isReRender,
            partialTick, packedLight, packedOverlay);
    }

    @Override
    protected void applyRotations(BloodTreeEntity tree, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - Mth.rotLerp(partialTick, tree.yRotO, tree.getYRot())));
    }
}
