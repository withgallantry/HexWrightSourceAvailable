package software.bernie.geckolib.renderer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

import javax.annotation.Nullable;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import net.minecraft.class_4608;
import java.util.List;

public interface GeoRenderer<T extends GeoAnimatable> {
	GeoModel<T> getGeoModel();

	T getAnimatable();

	default class_2960 getTextureLocation(T animatable) {
		return getGeoModel().getTextureResource(animatable, this);
	}

	default List<GeoRenderLayer<T>> getRenderLayers() {
		return List.of();
	}

	default class_1921 getRenderType(T animatable, class_2960 texture,
									 @Nullable class_4597 bufferSource,
									 float partialTick) {
		return getGeoModel().getRenderType(animatable, texture);
	}

	default Color getRenderColor(T animatable, float partialTick, int packedLight) {
		return Color.WHITE;
	}

	@Deprecated
	default int getPackedOverlay(T animatable, float u) {
		return class_4608.field_21444;
	}

	default int getPackedOverlay(T animatable, float u, float partialTick) {
		return getPackedOverlay(animatable, u);
	}

	default long getInstanceId(T animatable) {
		return animatable.hashCode();
	}

	default float getMotionAnimThreshold(T animatable) {
		return 0.015f;
	}

	default void defaultRender(class_4587 poseStack, T animatable, class_4597 bufferSource, @Nullable class_1921 renderType, @Nullable class_4588 buffer,
							   float yaw, float partialTick, int packedLight) {
		poseStack.method_22903();

		Color renderColor = getRenderColor(animatable, partialTick, packedLight);
		float red = renderColor.getRedFloat();
		float green = renderColor.getGreenFloat();
		float blue = renderColor.getBlueFloat();
		float alpha = renderColor.getAlphaFloat();
		int packedOverlay = getPackedOverlay(animatable, 0, partialTick);
		BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(animatable, this));

		if (renderType == null)
			renderType = getRenderType(animatable, getTextureLocation(animatable), bufferSource, partialTick);

		if (buffer == null)
			buffer = bufferSource.getBuffer(renderType);

		preRender(poseStack, animatable, model, bufferSource, buffer, false, partialTick, packedLight, packedOverlay, red, green, blue, alpha);

		if (firePreRenderEvent(poseStack, model, bufferSource, partialTick, packedLight)) {
			preApplyRenderLayers(poseStack, animatable, model, renderType, bufferSource, buffer, packedLight, packedLight, packedOverlay);
			actuallyRender(poseStack, animatable, model, renderType,
					bufferSource, buffer, false, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
			applyRenderLayers(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
			postRender(poseStack, animatable, model, bufferSource, buffer, false, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
			firePostRenderEvent(poseStack, model, bufferSource, partialTick, packedLight);
		}

		poseStack.method_22909();

		renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
		doPostRenderCleanup();
	}

	default void reRender(BakedGeoModel model, class_4587 poseStack, class_4597 bufferSource, T animatable,
						  class_1921 renderType, class_4588 buffer, float partialTick,
						  int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		poseStack.method_22903();
		preRender(poseStack, animatable, model, bufferSource, buffer, true, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
		actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, true, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
		postRender(poseStack, animatable, model, bufferSource, buffer, true, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
		poseStack.method_22909();
	}

	default void actuallyRender(class_4587 poseStack, T animatable, BakedGeoModel model, class_1921 renderType,
								class_4597 bufferSource, class_4588 buffer, boolean isReRender, float partialTick,
								int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		updateAnimatedTextureFrame(animatable);

		for (GeoBone group : model.topLevelBones()) {
			renderRecursively(poseStack, animatable, group, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
					packedOverlay, red, green, blue, alpha);
		}
	}

	default void preApplyRenderLayers(class_4587 poseStack, T animatable, BakedGeoModel model, class_1921 renderType, class_4597 bufferSource,
								   class_4588 buffer, float partialTick, int packedLight, int packedOverlay) {
		for (GeoRenderLayer<T> renderLayer : getRenderLayers()) {
			renderLayer.preRender(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
		}
	}

	default void applyRenderLayersForBone(class_4587 poseStack, T animatable, GeoBone bone, class_1921 renderType, class_4597 bufferSource,
										  class_4588 buffer, float partialTick, int packedLight, int packedOverlay) {
		for (GeoRenderLayer<T> renderLayer : getRenderLayers()) {
			renderLayer.renderForBone(poseStack, animatable, bone, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
		}
	}

	default void applyRenderLayers(class_4587 poseStack, T animatable, BakedGeoModel model, class_1921 renderType, class_4597 bufferSource,
								   class_4588 buffer, float partialTick, int packedLight, int packedOverlay) {
		for (GeoRenderLayer<T> renderLayer : getRenderLayers()) {
			renderLayer.render(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
		}
	}

	default void preRender(class_4587 poseStack, T animatable, BakedGeoModel model, @Nullable class_4597 bufferSource, @Nullable class_4588 buffer, boolean isReRender, float partialTick, int packedLight,
						   int packedOverlay, float red, float green, float blue, float alpha) {}

	default void postRender(class_4587 poseStack, T animatable, BakedGeoModel model, class_4597 bufferSource, class_4588 buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue,
							float alpha) {}

	default void renderFinal(class_4587 poseStack, T animatable, BakedGeoModel model, class_4597 bufferSource, class_4588 buffer, float partialTick, int packedLight,
							 int packedOverlay, float red, float green, float blue, float alpha) {}

	default void doPostRenderCleanup() {}

	default void renderRecursively(class_4587 poseStack, T animatable, GeoBone bone, class_1921 renderType, class_4597 bufferSource,
								   class_4588 buffer, boolean isReRender, float partialTick, int packedLight,
								   int packedOverlay, float red, float green, float blue, float alpha) {
		poseStack.method_22903();
		RenderUtils.prepMatrixForBone(poseStack, bone);
		renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);

		if (!isReRender)
			applyRenderLayersForBone(poseStack, getAnimatable(), bone, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);

		renderChildBones(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
		poseStack.method_22909();
	}

	default void renderCubesOfBone(class_4587 poseStack, GeoBone bone, class_4588 buffer, int packedLight,
								   int packedOverlay, float red, float green, float blue, float alpha) {
		if (bone.isHidden())
			return;

		for (GeoCube cube : bone.getCubes()) {
			poseStack.method_22903();
			renderCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha);
			poseStack.method_22909();
		}
	}

	default void renderChildBones(class_4587 poseStack, T animatable, GeoBone bone, class_1921 renderType, class_4597 bufferSource, class_4588 buffer,
								  boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		if (bone.isHidingChildren())
			return;

		for (GeoBone childBone : bone.getChildBones()) {
			renderRecursively(poseStack, animatable, childBone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
		}
	}

	default void renderCube(class_4587 poseStack, GeoCube cube, class_4588 buffer, int packedLight,
							int packedOverlay, float red, float green, float blue, float alpha) {
		RenderUtils.translateToPivotPoint(poseStack, cube);
		RenderUtils.rotateMatrixAroundCube(poseStack, cube);
		RenderUtils.translateAwayFromPivotPoint(poseStack, cube);

		Matrix3f normalisedPoseState = poseStack.method_23760().method_23762();
		Matrix4f poseState = new Matrix4f(poseStack.method_23760().method_23761());

		for (GeoQuad quad : cube.quads()) {
			if (quad == null)
				continue;

			Vector3f normal = normalisedPoseState.transform(new Vector3f(quad.normal()));

			RenderUtils.fixInvertedFlatCube(cube, normal);
			createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
		}
	}

	default void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, class_4588 buffer,
			int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		for (GeoVertex vertex : quad.vertices()) {
			Vector3f position = vertex.position();
			Vector4f vector4f = poseState.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0f));

			buffer.method_23919(vector4f.x(), vector4f.y(), vector4f.z(), red, green, blue, alpha, vertex.texU(),
					vertex.texV(), packedOverlay, packedLight, normal.x(), normal.y(), normal.z());
		}
	}

	void fireCompileRenderLayersEvent();

	boolean firePreRenderEvent(class_4587 poseStack, BakedGeoModel model, class_4597 bufferSource, float partialTick, int packedLight);

	void firePostRenderEvent(class_4587 poseStack, BakedGeoModel model, class_4597 bufferSource, float partialTick, int packedLight);

	default void scaleModelForRender(float widthScale, float heightScale, class_4587 poseStack, T animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
		if (!isReRender && (widthScale != 1 || heightScale != 1))
			poseStack.method_22905(widthScale, heightScale, widthScale);
	}

	void updateAnimatedTextureFrame(T animatable);
}
