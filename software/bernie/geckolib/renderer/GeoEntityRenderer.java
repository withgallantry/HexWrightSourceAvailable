package software.bernie.geckolib.renderer;

import net.minecraft.class_124;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1308;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1664;
import net.minecraft.class_1921;
import net.minecraft.class_1944;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_270;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_4050;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import net.minecraft.class_4608;
import net.minecraft.class_5617;
import net.minecraft.class_765;
import net.minecraft.class_7833;
import net.minecraft.class_7923;
import net.minecraft.class_897;
import net.minecraft.world.entity.*;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayersContainer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.List;

public class GeoEntityRenderer<T extends class_1297 & GeoAnimatable> extends class_897<T> implements GeoRenderer<T> {
	protected final GeoRenderLayersContainer<T> renderLayers = new GeoRenderLayersContainer<>(this);
	protected final GeoModel<T> model;

	protected T animatable;
	protected float scaleWidth = 1;
	protected float scaleHeight = 1;

	protected Matrix4f entityRenderTranslations = new Matrix4f();
	protected Matrix4f modelRenderTranslations = new Matrix4f();

    public GeoEntityRenderer(class_5617.class_5618 context, class_1299<? extends T> entityType) {
        this(context, new DefaultedEntityGeoModel<>(class_7923.field_41177.method_10221(entityType)));
    }

	public GeoEntityRenderer(class_5617.class_5618 renderManager, GeoModel<T> model) {
		super(renderManager);

		this.model = model;
	}

	@Override
	public GeoModel<T> getGeoModel() {
		return this.model;
	}

	@Override
	public T getAnimatable() {
		return this.animatable;
	}

	@Override
	public long getInstanceId(T animatable) {
		return animatable.method_5628();
	}

	@Override
	public class_2960 method_3931(T animatable) {
		return GeoRenderer.super.getTextureLocation(animatable);
	}

	@Override
	public List<GeoRenderLayer<T>> getRenderLayers() {
		return this.renderLayers.getRenderLayers();
	}

	public GeoEntityRenderer<T> addRenderLayer(GeoRenderLayer<T> renderLayer) {
		this.renderLayers.addLayer(renderLayer);

		return this;
	}

	public GeoEntityRenderer<T> withScale(float scale) {
		return withScale(scale, scale);
	}

	public GeoEntityRenderer<T> withScale(float scaleWidth, float scaleHeight) {
		this.scaleWidth = scaleWidth;
		this.scaleHeight = scaleHeight;

		return this;
	}

	@Override
	public void preRender(class_4587 poseStack, T animatable, BakedGeoModel model, class_4597 bufferSource, class_4588 buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue,
						  float alpha) {
		this.entityRenderTranslations = new Matrix4f(poseStack.method_23760().method_23761());

		scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
	}

	@Override
	public void method_3936(T entity, float entityYaw, float partialTick, class_4587 poseStack, class_4597 bufferSource, int packedLight) {
		this.animatable = entity;

		defaultRender(poseStack, entity, bufferSource, null, null, entityYaw, partialTick, packedLight);
	}

	@Override
	public void actuallyRender(class_4587 poseStack, T animatable, BakedGeoModel model, class_1921 renderType, class_4597 bufferSource, class_4588 buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		poseStack.method_22903();

		class_1309 livingEntity = animatable instanceof class_1309 entity ? entity : null;
		boolean shouldSit = animatable.method_5765() && (animatable.method_5854() != null);
		float lerpBodyRot = livingEntity == null ? 0 : class_3532.method_17821(partialTick, livingEntity.field_6220, livingEntity.field_6283);
		float lerpHeadRot = livingEntity == null ? 0 : class_3532.method_17821(partialTick, livingEntity.field_6259, livingEntity.field_6241);
		float netHeadYaw = lerpHeadRot - lerpBodyRot;

		if (shouldSit && animatable.method_5854() instanceof class_1309 livingentity) {
			lerpBodyRot = class_3532.method_17821(partialTick, livingentity.field_6220, livingentity.field_6283);
			netHeadYaw = lerpHeadRot - lerpBodyRot;
			float clampedHeadYaw = class_3532.method_15363(class_3532.method_15393(netHeadYaw), -85, 85);
			lerpBodyRot = lerpHeadRot - clampedHeadYaw;

			if (clampedHeadYaw * clampedHeadYaw > 2500f)
				lerpBodyRot += clampedHeadYaw * 0.2f;

			netHeadYaw = lerpHeadRot - lerpBodyRot;
		}

		if (animatable.method_18376() == class_4050.field_18078 && livingEntity != null) {
			class_2350 bedDirection = livingEntity.method_18401();

			if (bedDirection != null) {
				float eyePosOffset = livingEntity.method_18381(class_4050.field_18076) - 0.1F;

				poseStack.method_46416(-bedDirection.method_10148() * eyePosOffset, 0, -bedDirection.method_10165() * eyePosOffset);
			}
		}

		float ageInTicks = animatable.field_6012 + partialTick;
		float limbSwingAmount = 0;
		float limbSwing = 0;

		applyRotations(animatable, poseStack, ageInTicks, lerpBodyRot, partialTick);

		if (!shouldSit && animatable.method_5805() && livingEntity != null) {
			limbSwingAmount = livingEntity.field_42108.method_48570(partialTick);
			limbSwing = livingEntity.field_42108.method_48572(partialTick);

			if (livingEntity.method_6109())
				limbSwing *= 3f;

			if (limbSwingAmount > 1f)
				limbSwingAmount = 1f;
		}

		if (!isReRender) {
			float headPitch = class_3532.method_16439(partialTick, animatable.field_6004, animatable.method_36455());
			float motionThreshold = getMotionAnimThreshold(animatable);
			class_243 velocity = animatable.method_18798();
			float avgVelocity = (float)((Math.abs(velocity.field_1352) + Math.abs(velocity.field_1350)) / 2f);
			AnimationState<T> animationState = new AnimationState<T>(animatable, limbSwing, limbSwingAmount, partialTick, avgVelocity >= motionThreshold && limbSwingAmount != 0);
			long instanceId = getInstanceId(animatable);
			GeoModel<T> currentModel = getGeoModel();

			animationState.setData(DataTickets.TICK, animatable.getTick(animatable));
			animationState.setData(DataTickets.ENTITY, animatable);
			animationState.setData(DataTickets.ENTITY_MODEL_DATA, new EntityModelData(shouldSit, livingEntity != null && livingEntity.method_6109(), -netHeadYaw, -headPitch));
			currentModel.addAdditionalStateData(animatable, instanceId, animationState::setData);
			currentModel.handleAnimations(animatable, instanceId, animationState);
		}

		poseStack.method_46416(0, 0.01f, 0);

		this.modelRenderTranslations = new Matrix4f(poseStack.method_23760().method_23761());

		if (animatable.method_5756(class_310.method_1551().field_1724)) {
			if (class_310.method_1551().method_27022(animatable)) {
				buffer = bufferSource.getBuffer(renderType = class_1921.method_23287(method_3931(animatable)));
			}
			else {
				renderType = null;
			}
		}

		if (renderType != null)
			GeoRenderer.super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick,
					packedLight, packedOverlay, red, green, blue, alpha);

		poseStack.method_22909();
	}

	@Override
	public void applyRenderLayers(class_4587 poseStack, T animatable, BakedGeoModel model, class_1921 renderType,
								  class_4597 bufferSource, class_4588 buffer, float partialTick,
								  int packedLight, int packedOverlay) {
		if (!animatable.method_7325())
			GeoRenderer.super.applyRenderLayers(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
	}

	@Override
	public void renderFinal(class_4587 poseStack, T animatable, BakedGeoModel model, class_4597 bufferSource, class_4588 buffer, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		super.method_3936(animatable, 0, partialTick, poseStack, bufferSource, packedLight);

		if (animatable instanceof class_1308 mob) {
			class_1297 leashHolder = mob.method_5933();

			if (leashHolder != null)
				renderLeash(mob, partialTick, poseStack, bufferSource, leashHolder);
		}
	}

	@Override
	public void doPostRenderCleanup() {
		this.animatable = null;
	}

	@Override
	public void renderRecursively(class_4587 poseStack, T animatable, GeoBone bone, class_1921 renderType, class_4597 bufferSource, class_4588 buffer, boolean isReRender, float partialTick, int packedLight,
								  int packedOverlay, float red, float green, float blue, float alpha) {
		poseStack.method_22903();
		RenderUtils.translateMatrixToBone(poseStack, bone);
		RenderUtils.translateToPivotPoint(poseStack, bone);
		RenderUtils.rotateMatrixAroundBone(poseStack, bone);
		RenderUtils.scaleMatrixForBone(poseStack, bone);

		if (bone.isTrackingMatrices()) {
			Matrix4f poseState = new Matrix4f(poseStack.method_23760().method_23761());
			Matrix4f localMatrix = RenderUtils.invertAndMultiplyMatrices(poseState, this.entityRenderTranslations);

			bone.setModelSpaceMatrix(RenderUtils.invertAndMultiplyMatrices(poseState, this.modelRenderTranslations));
			bone.setLocalSpaceMatrix(RenderUtils.translateMatrix(localMatrix, method_23169(this.animatable, 1).method_46409()));
			bone.setWorldSpaceMatrix(RenderUtils.translateMatrix(new Matrix4f(localMatrix), this.animatable.method_19538().method_46409()));
		}

		RenderUtils.translateAwayFromPivotPoint(poseStack, bone);

		renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);

		if (!isReRender)
			applyRenderLayersForBone(poseStack, animatable, bone, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);

		renderChildBones(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);

		poseStack.method_22909();
	}

	protected void applyRotations(T animatable, class_4587 poseStack, float ageInTicks, float rotationYaw,
			float partialTick) {
		class_4050 pose = animatable.method_18376();
		class_1309 livingEntity = animatable instanceof class_1309 entity ? entity : null;

		if (isShaking(animatable))
			rotationYaw += (float)(Math.cos(animatable.field_6012 * 3.25d) * Math.PI * 0.4d);

		if (pose != class_4050.field_18078)
			poseStack.method_22907(class_7833.field_40716.rotationDegrees(180f - rotationYaw));

		if (livingEntity != null && livingEntity.field_6213 > 0) {
			float deathRotation = (livingEntity.field_6213 + partialTick - 1f) / 20f * 1.6f;

			poseStack.method_22907(class_7833.field_40718.rotationDegrees(Math.min(class_3532.method_15355(deathRotation), 1) * getDeathMaxRotation(animatable)));
		}
		else if (livingEntity != null && livingEntity.method_6123()) {
			poseStack.method_22907(class_7833.field_40714.rotationDegrees(-90f - livingEntity.method_36455()));
			poseStack.method_22907(class_7833.field_40716.rotationDegrees((livingEntity.field_6012 + partialTick) * -75f));
		}
		else if (livingEntity != null && pose == class_4050.field_18078) {
			class_2350 bedOrientation = livingEntity.method_18401();

			poseStack.method_22907(class_7833.field_40716.rotationDegrees(bedOrientation != null ? RenderUtils.getDirectionAngle(bedOrientation) : rotationYaw));
			poseStack.method_22907(class_7833.field_40718.rotationDegrees(getDeathMaxRotation(animatable)));
			poseStack.method_22907(class_7833.field_40716.rotationDegrees(270f));
		}
		else if (animatable.method_16914() || animatable instanceof class_1657) {
			String name = animatable.method_5477().getString();

			if (animatable instanceof class_1657 player) {
				if (!player.method_7348(class_1664.field_7559))
					return;
			}
			else {
				name = class_124.method_539(name);
			}

			if (name != null && (name.equals("Dinnerbone") || name.equalsIgnoreCase("Grumm"))) {
				poseStack.method_46416(0, animatable.method_17682() + 0.1f, 0);
				poseStack.method_22907(class_7833.field_40718.rotationDegrees(180f));
			}
		}
	}

	protected float getDeathMaxRotation(T animatable) {
		return 90f;
	}

	public double getNameRenderCutoffDistance(T animatable) {
		return animatable.method_21751() ? 32d : 64d;
	}

	@Override
	public boolean method_3921(T animatable) {
		if (!(animatable instanceof class_1309))
			return super.method_3921(animatable);

		double nameRenderCutoff = getNameRenderCutoffDistance(animatable);

		if (this.field_4676.method_23168(animatable) >= nameRenderCutoff * nameRenderCutoff)
			return false;

		if (animatable instanceof class_1308 && (!animatable.method_5733() && (!animatable.method_16914() || animatable != this.field_4676.field_4678)))
			return false;

		final class_310 minecraft = class_310.method_1551();
		boolean visibleToClient = !animatable.method_5756(minecraft.field_1724);
		class_270 entityTeam = animatable.method_5781();

		if (entityTeam == null)
			return class_310.method_1498() && animatable != minecraft.method_1560() && visibleToClient && !animatable.method_5782();

		class_270 playerTeam = minecraft.field_1724.method_5781();

		return switch (entityTeam.method_1201()) {
			case field_1442 -> visibleToClient;
			case field_1443 -> false;
			case field_1444 -> playerTeam == null ? visibleToClient : entityTeam.method_1206(playerTeam) && (entityTeam.method_1199() || visibleToClient);
			case field_1446 -> playerTeam == null ? visibleToClient : !entityTeam.method_1206(playerTeam) && visibleToClient;
		};
	}

	@Override
	public int getPackedOverlay(T animatable, float u) {
		if (!(animatable instanceof class_1309 entity))
			return class_4608.field_21444;

		return class_4608.method_23625(class_4608.method_23210(u),
				class_4608.method_23212(entity.field_6235 > 0 || entity.field_6213 > 0));
	}

	@Override
	public int getPackedOverlay(T animatable, float u, float partialTick) {
		return getPackedOverlay(animatable, u);
	}

	public boolean isShaking(T animatable) {
		return animatable.method_32314();
	}

	public <E extends class_1297, M extends class_1308> void renderLeash(M mob, float partialTick, class_4587 poseStack,
			class_4597 bufferSource, E leashHolder) {
		double lerpBodyAngle = (class_3532.method_16439(partialTick, mob.field_6220, mob.field_6283) * class_3532.field_29847) + class_3532.field_29845;
		class_243 leashOffset = mob.method_29919();
		double xAngleOffset = Math.cos(lerpBodyAngle) * leashOffset.field_1350 + Math.sin(lerpBodyAngle) * leashOffset.field_1352;
		double zAngleOffset = Math.sin(lerpBodyAngle) * leashOffset.field_1350 - Math.cos(lerpBodyAngle) * leashOffset.field_1352;
		double lerpOriginX = class_3532.method_16436(partialTick, mob.field_6014, mob.method_23317()) + xAngleOffset;
		double lerpOriginY = class_3532.method_16436(partialTick, mob.field_6036, mob.method_23318()) + leashOffset.field_1351;
		double lerpOriginZ = class_3532.method_16436(partialTick, mob.field_5969, mob.method_23321()) + zAngleOffset;
		class_243 ropeGripPosition = leashHolder.method_30951(partialTick);
		float xDif = (float)(ropeGripPosition.field_1352 - lerpOriginX);
		float yDif = (float)(ropeGripPosition.field_1351 - lerpOriginY);
		float zDif = (float)(ropeGripPosition.field_1350 - lerpOriginZ);
		float offsetMod = class_3532.method_48119(xDif * xDif + zDif * zDif) * 0.025f / 2f;
		float xOffset = zDif * offsetMod;
		float zOffset = xDif * offsetMod;
		class_4588 vertexConsumer = bufferSource.getBuffer(class_1921.method_23587());
		class_2338 entityEyePos = class_2338.method_49638(mob.method_5836(partialTick));
		class_2338 holderEyePos = class_2338.method_49638(leashHolder.method_5836(partialTick));
		int entityBlockLight = method_24087((T)mob, entityEyePos);
		int holderBlockLight = leashHolder.method_5809() ? 15 : leashHolder.method_37908().method_8314(class_1944.field_9282, holderEyePos);
		int entitySkyLight = mob.method_37908().method_8314(class_1944.field_9284, entityEyePos);
		int holderSkyLight = mob.method_37908().method_8314(class_1944.field_9284, holderEyePos);

		poseStack.method_22903();
		poseStack.method_22904(xAngleOffset, leashOffset.field_1351, zAngleOffset);

		Matrix4f posMatrix = new Matrix4f(poseStack.method_23760().method_23761());

		for (int segment = 0; segment <= 24; ++segment) {
			GeoEntityRenderer.renderLeashPiece(vertexConsumer, posMatrix, xDif, yDif, zDif, entityBlockLight, holderBlockLight,
					entitySkyLight, holderSkyLight, 0.025f, 0.025f, xOffset, zOffset, segment, false);
		}

		for (int segment = 24; segment >= 0; --segment) {
			GeoEntityRenderer.renderLeashPiece(vertexConsumer, posMatrix, xDif, yDif, zDif, entityBlockLight, holderBlockLight,
					entitySkyLight, holderSkyLight, 0.025f, 0.0f, xOffset, zOffset, segment, true);
		}

		poseStack.method_22909();
	}

	private static void renderLeashPiece(class_4588 buffer, Matrix4f positionMatrix, float xDif, float yDif,
										 float zDif, int entityBlockLight, int holderBlockLight, int entitySkyLight,
										 int holderSkyLight, float width, float yOffset, float xOffset, float zOffset, int segment, boolean isLeashKnot) {
		float piecePosPercent = segment / 24f;
		int lerpBlockLight = (int)class_3532.method_16439(piecePosPercent, entityBlockLight, holderBlockLight);
		int lerpSkyLight = (int)class_3532.method_16439(piecePosPercent, entitySkyLight, holderSkyLight);
		int packedLight = class_765.method_23687(lerpBlockLight, lerpSkyLight);
		float knotColourMod = segment % 2 == (isLeashKnot ? 1 : 0) ? 0.7f : 1f;
		float red = 0.5f * knotColourMod;
		float green = 0.4f * knotColourMod;
		float blue = 0.3f * knotColourMod;
		float x = xDif * piecePosPercent;
		float y = yDif > 0.0f ? yDif * piecePosPercent * piecePosPercent : yDif - yDif * (1.0f - piecePosPercent) * (1.0f - piecePosPercent);
		float z = zDif * piecePosPercent;

		buffer.method_22918(positionMatrix, x - xOffset, y + yOffset, z + zOffset).method_22915(red, green, blue, 1).method_22916(packedLight).method_1344();
		buffer.method_22918(positionMatrix, x + xOffset, y + width - yOffset, z - zOffset).method_22915(red, green, blue, 1).method_22916(packedLight).method_1344();
	}

	@Override
	public void updateAnimatedTextureFrame(T animatable) {
		AnimatableTexture.setAndUpdate(method_3931(animatable));
	}

	@Override
	public void fireCompileRenderLayersEvent() {
		GeoRenderEvent.Entity.CompileRenderLayers.EVENT.invoker().handle(new GeoRenderEvent.Entity.CompileRenderLayers(this));
	}

	@Override
	public boolean firePreRenderEvent(class_4587 poseStack, BakedGeoModel model, class_4597 bufferSource, float partialTick, int packedLight) {
		return GeoRenderEvent.Entity.Pre.EVENT.invoker().handle(new GeoRenderEvent.Entity.Pre(this, poseStack, model, bufferSource, partialTick, packedLight));
	}

	@Override
	public void firePostRenderEvent(class_4587 poseStack, BakedGeoModel model, class_4597 bufferSource, float partialTick, int packedLight) {
		GeoRenderEvent.Entity.Post.EVENT.invoker().handle(new GeoRenderEvent.Entity.Post(this, poseStack, model, bufferSource, partialTick, packedLight));
	}
}
