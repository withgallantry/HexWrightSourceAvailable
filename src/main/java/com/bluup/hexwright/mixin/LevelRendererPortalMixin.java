package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.lowdragmc.photon.client.gameobject.emitter.PhotonParticleRenderType;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererPortalMixin {

    @Shadow
    @Final
    @Mutable
    private ObjectArrayList<Object> renderChunksInFrustum;

    @Shadow
    private ViewArea viewArea;

    @Shadow
    @Final
    private java.util.concurrent.atomic.AtomicReference<?> renderChunkStorage;

    @Unique
    private final ObjectArrayList<Object> hexwright$portalSections = new ObjectArrayList<>();

    @Unique
    private ObjectArrayList<Object> hexwright$mainSections;

    @Shadow
    private double xTransparentOld;

    @Shadow
    private double yTransparentOld;

    @Shadow
    private double zTransparentOld;

    @Unique
    private double hexwright$mainTransparentX;

    @Unique
    private double hexwright$mainTransparentY;

    @Unique
    private double hexwright$mainTransparentZ;

    @Shadow
    private VertexBuffer cloudBuffer;

    @Shadow
    private int prevCloudX;

    @Shadow
    private int prevCloudY;

    @Shadow
    private int prevCloudZ;

    @Shadow
    private Vec3 prevCloudColor;

    @Shadow
    private CloudStatus prevCloudsType;

    @Shadow
    private boolean generateClouds;

    @Unique
    private VertexBuffer hexwright$portalCloudBuffer;

    @Unique
    private int hexwright$portalPrevCloudX;

    @Unique
    private int hexwright$portalPrevCloudY;

    @Unique
    private int hexwright$portalPrevCloudZ;

    @Unique
    private Vec3 hexwright$portalPrevCloudColor = Vec3.ZERO;

    @Unique
    private CloudStatus hexwright$portalPrevCloudsType;

    @Unique
    private boolean hexwright$portalGenerateClouds = true;

    @Unique
    private VertexBuffer hexwright$mainCloudBuffer;

    @Unique
    private int hexwright$mainPrevCloudX;

    @Unique
    private int hexwright$mainPrevCloudY;

    @Unique
    private int hexwright$mainPrevCloudZ;

    @Unique
    private Vec3 hexwright$mainPrevCloudColor;

    @Unique
    private CloudStatus hexwright$mainPrevCloudsType;

    @Unique
    private boolean hexwright$mainGenerateClouds;

    @Unique
    private RendererSetting.Layer hexwright$mainPhotonLayer;

    @Unique
    private Frustum hexwright$mainPhotonFrustum;

    @Unique
    private boolean hexwright$mainPhotonBloomMark;

    @Unique
    private Object hexwright$mainIrisParticlePhase;

    @Unique
    private com.bluup.hexwright.client.portal.SodiumPortalCompat.Context hexwright$portalSodiumContext;

    @Unique
    private void hexwright$swapInPortalState() {
        hexwright$mainSections = renderChunksInFrustum;
        renderChunksInFrustum = hexwright$portalSections;

        hexwright$mainTransparentX = xTransparentOld;
        hexwright$mainTransparentY = yTransparentOld;
        hexwright$mainTransparentZ = zTransparentOld;
        Vec3 folded = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        xTransparentOld = folded.x;
        yTransparentOld = folded.y;
        zTransparentOld = folded.z;

        hexwright$mainCloudBuffer = cloudBuffer;
        hexwright$mainPrevCloudX = prevCloudX;
        hexwright$mainPrevCloudY = prevCloudY;
        hexwright$mainPrevCloudZ = prevCloudZ;
        hexwright$mainPrevCloudColor = prevCloudColor;
        hexwright$mainPrevCloudsType = prevCloudsType;
        hexwright$mainGenerateClouds = generateClouds;
        cloudBuffer = hexwright$portalCloudBuffer;
        prevCloudX = hexwright$portalPrevCloudX;
        prevCloudY = hexwright$portalPrevCloudY;
        prevCloudZ = hexwright$portalPrevCloudZ;
        prevCloudColor = hexwright$portalPrevCloudColor;
        prevCloudsType = hexwright$portalPrevCloudsType;
        generateClouds = hexwright$portalGenerateClouds;

        hexwright$mainPhotonLayer = PhotonRenderStateAccessor.hexwright$getLayer();
        hexwright$mainPhotonFrustum = PhotonRenderStateAccessor.hexwright$getFrustum();
        hexwright$mainPhotonBloomMark = PhotonParticleRenderType.bloomMark;

        hexwright$mainIrisParticlePhase = com.bluup.hexwright.client.portal.IrisParticlePhase.save();

        if (com.bluup.hexwright.client.portal.SodiumPortalCompat.isActive()) {
            if (hexwright$portalSodiumContext == null) {
                hexwright$portalSodiumContext = com.bluup.hexwright.client.portal.SodiumPortalCompat
                    .newContext(Minecraft.getInstance().options.getEffectiveRenderDistance());
            }
            com.bluup.hexwright.client.portal.SodiumPortalCompat.swap(hexwright$portalSodiumContext);
        }
    }

    @Unique
    private void hexwright$swapOutPortalState() {
        renderChunksInFrustum = hexwright$mainSections;
        hexwright$mainSections = null;

        xTransparentOld = hexwright$mainTransparentX;
        yTransparentOld = hexwright$mainTransparentY;
        zTransparentOld = hexwright$mainTransparentZ;

        hexwright$portalCloudBuffer = cloudBuffer;
        hexwright$portalPrevCloudX = prevCloudX;
        hexwright$portalPrevCloudY = prevCloudY;
        hexwright$portalPrevCloudZ = prevCloudZ;
        hexwright$portalPrevCloudColor = prevCloudColor;
        hexwright$portalPrevCloudsType = prevCloudsType;
        hexwright$portalGenerateClouds = generateClouds;
        cloudBuffer = hexwright$mainCloudBuffer;
        prevCloudX = hexwright$mainPrevCloudX;
        prevCloudY = hexwright$mainPrevCloudY;
        prevCloudZ = hexwright$mainPrevCloudZ;
        prevCloudColor = hexwright$mainPrevCloudColor;
        prevCloudsType = hexwright$mainPrevCloudsType;
        generateClouds = hexwright$mainGenerateClouds;

        PhotonRenderStateAccessor.hexwright$setLayer(hexwright$mainPhotonLayer);
        PhotonRenderStateAccessor.hexwright$setFrustum(hexwright$mainPhotonFrustum);
        PhotonParticleRenderType.bloomMark = hexwright$mainPhotonBloomMark;

        com.bluup.hexwright.client.portal.IrisParticlePhase.restore(hexwright$mainIrisParticlePhase);
        hexwright$mainIrisParticlePhase = null;

        com.bluup.hexwright.client.portal.SodiumPortalCompat.swap(hexwright$portalSodiumContext);
    }

    @Shadow
    private ChunkRenderDispatcher chunkRenderDispatcher;


    @Shadow
    @Nullable
    private PostChain transparencyChain;

    @Shadow
    @Nullable
    private RenderTarget translucentTarget;

    @Shadow
    @Nullable
    private RenderTarget itemEntityTarget;

    @Shadow
    @Nullable
    private RenderTarget particlesTarget;

    @Shadow
    @Nullable
    private RenderTarget weatherTarget;

    @Shadow
    @Nullable
    private RenderTarget cloudsTarget;

    @Unique
    private boolean hexwright$hideFabulousTargets() {
        return PortalViewRenderer.isRenderingView() && PortalViewRenderer.FABULOUS_VIEWS;
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;transparencyChain:Lnet/minecraft/client/renderer/PostChain;"))
    private PostChain hexwright$noTransparencyChainInPass(LevelRenderer self) {
        return hexwright$hideFabulousTargets() ? null : transparencyChain;
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;translucentTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget hexwright$noTranslucentTargetInPass(LevelRenderer self) {
        return hexwright$hideFabulousTargets() ? null : translucentTarget;
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;itemEntityTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget hexwright$noItemEntityTargetInPass(LevelRenderer self) {
        return hexwright$hideFabulousTargets() ? null : itemEntityTarget;
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;particlesTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget hexwright$noParticlesTargetInPass(LevelRenderer self) {
        return hexwright$hideFabulousTargets() ? null : particlesTarget;
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;weatherTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget hexwright$noWeatherTargetInPass(LevelRenderer self) {
        return hexwright$hideFabulousTargets() ? null : weatherTarget;
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;cloudsTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget hexwright$noCloudsTargetInPass(LevelRenderer self) {
        return hexwright$hideFabulousTargets() ? null : cloudsTarget;
    }


    @Inject(method = "compileChunks", at = @At("HEAD"), cancellable = true)
    private void hexwright$budgetPortalChunkBuilds(Camera camera, CallbackInfo ci) {
        if (PortalViewRenderer.isRenderingView()) {
            PortalViewRenderer.compilePortalChunks(chunkRenderDispatcher);
            ci.cancel();
        } else if (PortalViewRenderer.isFreshCrossingCompileActive()) {
            PortalViewRenderer.compileFreshCrossingChunks(chunkRenderDispatcher);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true)
    private Matrix4f hexwright$beginPortalPass(Matrix4f projection) {
        if (PortalViewRenderer.isRenderingView() && hexwright$mainSections == null) {
            hexwright$swapInPortalState();
        }
        if (!PortalViewRenderer.isRenderingView()) {
            PortalViewRenderer.ensureScissorClear();
            PortalViewRenderer.ensureBloomPointsAtMain();
        }
        Matrix4f clipped = PortalViewRenderer.beginPortalPass(projection);
        if (clipped != projection) {
            RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
        }
        return clipped;
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void hexwright$endPortalPass(CallbackInfo ci) {
        PortalViewRenderer.endPortalPass();
        if (hexwright$mainSections != null) {
            hexwright$swapOutPortalState();
        }
    }

    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    private void hexwright$isolatedPortalVisibility(Camera camera, Frustum frustum,
                                                   boolean hasCapturedFrustum, boolean isSpectator,
                                                   CallbackInfo ci) {
        if (!PortalViewRenderer.isRenderingView() || viewArea == null) {
            return;
        }
        if (com.bluup.hexwright.client.portal.SodiumPortalCompat.isActive()) {
            return;
        }
        PortalViewRenderer.fillPortalVisibility(
            renderChunksInFrustum, viewArea.chunks, camera, frustum);
        ci.cancel();
    }

    @Inject(method = "setupRender", at = @At("TAIL"))
    private void hexwright$freshCrossingVisibility(Camera camera, Frustum frustum,
                                                   boolean hasCapturedFrustum, boolean isSpectator,
                                                   CallbackInfo ci) {
        if (PortalViewRenderer.isRenderingView() || viewArea == null) {
            return;
        }
        int graphSections = PortalViewRenderer.graphSectionCount(renderChunkStorage.get());
        boolean filled = PortalViewRenderer.consumeFreshCrossingFrame();
        if (filled) {
            PortalViewRenderer.fillPortalVisibility(
                renderChunksInFrustum, viewArea.chunks, camera, frustum);
        }
        PortalViewRenderer.debugMainFrame(renderChunksInFrustum.size(), graphSections, filled);
    }
}
