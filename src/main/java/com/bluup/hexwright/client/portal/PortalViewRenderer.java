package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.mixin.BloomEffectAccessor;
import com.bluup.hexwright.mixin.GameRendererAccessor;
import com.bluup.hexwright.mixin.LevelRendererAccessor;
import com.bluup.hexwright.mixin.MinecraftPortalAccessor;
import com.bluup.hexwright.server.portal.PortalPair;
import com.bluup.hexwright.server.portal.PortalTransform;
import com.bluup.hexwright.server.portal.PortalWindow;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;

public final class PortalViewRenderer {

    private static final int MAX_VIEWS = Math.max(1,
        Integer.getInteger("hexwright.portal.views.max", 3));

    private static final int MAX_NESTED_VIEWS = 1;

    private static final int MAX_PASSES_PER_FRAME = Math.max(1,
        Integer.getInteger("hexwright.portal.passes", 4));

    private static final int DEPTH_CEILING = Math.max(1, Math.min(4,
        Integer.getInteger("hexwright.portal.depth", 2)));

    private static final double NESTED_VIEW_DISTANCE = 48.0;

    private static final double NESTED_MIN_SOLID_ANGLE = 0.004;

    private static final double VIEW_DISTANCE = 96.0;

    private static final long TARGET_TTL_FRAMES = 600;

    private static final int MAX_TARGETS = 6;

    private static final double MIN_SOLID_ANGLE = 0.0002;

    private static final double OFF_SCREEN_MARGIN = Math.toRadians(20.0);

    private static final double CLIP_BIAS = 0.01;

    private static final double MIN_CLIP_DEPTH = 0.05;

    private static final int SCISSOR_MARGIN = 8;

    private static final double MIN_SCISSOR_DEPTH = 1.0;

    public record ViewKey(UUID pairId, int side) {
    }

    public record PassKey(ViewKey pane, @Nullable PassKey parent) {

        boolean mentionsDeadPair(Set<UUID> livePairs) {
            return !livePairs.contains(pane.pairId())
                || (parent != null && parent.mentionsDeadPair(livePairs));
        }
    }

    private static final class Target {
        TextureTarget target;
        long lastUsedFrame;
    }

    private static final Map<PassKey, Target> TARGETS = new HashMap<>();
    private static final Set<PassKey> RENDERED_THIS_FRAME = new HashSet<>();

    private static final Set<ViewKey> RENDERED_LAST_FRAME = new HashSet<>();

    private static final double INCUMBENT_BIAS = 0.7;


    private static final boolean IRIS_VIEWS_ENABLED =
        !"false".equals(System.getProperty("hexwright.portal.iris"));

    private static final boolean VIEWS_ENABLED =
        !"false".equals(System.getProperty("hexwright.portal.views"));

    private static final boolean CONE_ENABLED =
        !"false".equals(System.getProperty("hexwright.portal.cone"));

    private static final boolean SCISSOR_ENABLED =
        !"false".equals(System.getProperty("hexwright.portal.scissor"));

    private static final boolean FRESH_FILL_ENABLED =
        !"false".equals(System.getProperty("hexwright.portal.freshfill"));

    private static final boolean DEBUG =
        "true".equals(System.getProperty("hexwright.portal.debug"));

    public static final boolean FABULOUS_VIEWS =
        !"false".equals(System.getProperty("hexwright.portal.fabulous"));

    public static final boolean PANE_BLOOM =
        "true".equals(System.getProperty("hexwright.portal.panebloom"));

    static {
        if (DEBUG || !VIEWS_ENABLED || !CONE_ENABLED || !SCISSOR_ENABLED || !FRESH_FILL_ENABLED) {
            com.bluup.hexwright.Hexwright.LOGGER.info(
                "[portal-debug] toggles: views={} cone={} scissor={} freshfill={} debug={}",
                VIEWS_ENABLED, CONE_ENABLED, SCISSOR_ENABLED, FRESH_FILL_ENABLED, DEBUG);
        }
    }

    private static int debugLastSize = -1;
    private static String debugLastSource = "none";

    public static void debugMainFrame(int drawnSections, int graphSections, boolean freshFill) {
        if (!DEBUG) {
            return;
        }
        String source = freshFill ? "fill" : "graph";
        int delta = Math.abs(drawnSections - debugLastSize);
        if (debugLastSize >= 0
            && (!source.equals(debugLastSource) || delta > Math.max(20, debugLastSize / 6))) {
            com.bluup.hexwright.Hexwright.LOGGER.info(
                "[portal-debug] drawn {} -> {} | GRAPH {} -> {} | source {} -> {} | freshPhase={} frame#{} views={} nearBuilt={}",
                debugLastSize, drawnSections, debugLastGraph, graphSections,
                debugLastSource, source,
                freshPhase, freshCrossingFrames, RENDERED_THIS_FRAME.size(), freshNearFieldBuilt);
        }
        debugLastSize = drawnSections;
        debugLastGraph = graphSections;
        debugLastSource = source;
    }

    public static int graphSectionCount(@Nullable Object storage) {
        if (storage == null) {
            return -1;
        }
        try {
            java.lang.reflect.Field field = graphSetField;
            if (field == null) {
                for (java.lang.reflect.Field candidate : storage.getClass().getDeclaredFields()) {
                    if (candidate.getType() == java.util.LinkedHashSet.class) {
                        candidate.setAccessible(true);
                        field = candidate;
                        graphSetField = candidate;
                        break;
                    }
                }
            }
            if (field == null) {
                return -1;
            }
            Object set = field.get(storage);
            return set instanceof java.util.Collection<?> collection ? collection.size() : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static @Nullable java.lang.reflect.Field graphSetField;
    private static int debugLastGraph = -1;

    private static final int MAX_FRESH_CROSSING_FRAMES = 240;

    private static final int FRESH_OFF = 0;
    private static final int FRESH_FILLING = 1;
    private static final int FRESH_FINAL_REBUILD = 2;
    private static final int FRESH_GRACE = 3;

    private static int freshPhase = FRESH_OFF;
    private static int freshCrossingFrames;
    private static @Nullable Vec3 freshArrival;
    private static boolean freshNearFieldBuilt;
    private static boolean freshFillThisFrame;

    private static long frame;
    private static int passDepth;
    private static int passesThisFrame;
    private static int pendingTopLevelPasses;
    private static @Nullable PassKey activePassKey;
    private static @Nullable PortalFold activeTransform;
    private static @Nullable PortalWindow activeClipWindow;
    private static @Nullable ViewKey activeDestinationPane;
    private static @Nullable PortalCone activeCone;
    private static @Nullable AABB arrivalBox;
    private static boolean scissorActive;
    private static int scissorX;
    private static int scissorY;
    private static int scissorWidth;
    private static int scissorHeight;

    private PortalViewRenderer() {
    }

    public static int maxDepth() {
        int configured = switch (PortalOptions.views()) {
            case SHIMMER -> 0;
            case PANES -> 1;
            case FULL -> DEPTH_CEILING;
        };
        if (!IrisCompat.isShaderPackActive()) {
            return configured;
        }
        return IRIS_VIEWS_ENABLED ? Math.min(configured, 1) : 0;
    }

    public static boolean isRenderingView() {
        return passDepth > 0;
    }

    public static @Nullable PortalFold cameraTransform() {
        return activeTransform;
    }

    public static @Nullable Vec3 unfoldedCameraPosition() {
        return unfoldedCameraPos;
    }

    public static @Nullable BlockPos unfoldedCameraBlock() {
        return unfoldedCameraBlockPos;
    }

    public static void recordUnfoldedCamera(Camera camera) {
        unfoldedCameraPos = camera.getPosition();
        unfoldedCameraBlockPos = camera.getBlockPosition();
    }

    private static volatile @Nullable Vec3 unfoldedCameraPos;
    private static volatile @Nullable BlockPos unfoldedCameraBlockPos;

    public static @Nullable ViewKey activeDestinationPane() {
        return passDepth > 0 ? activeDestinationPane : null;
    }

    public static @Nullable Vec3 activeDestinationCenter() {
        return passDepth > 0 && activeClipWindow != null ? activeClipWindow.center() : null;
    }

    public static @Nullable PortalCone activeCone() {
        return activeCone;
    }

    public static int viewTextureId(UUID pairId, int side) {
        PassKey key = new PassKey(new ViewKey(pairId, side), activePassKey);
        if (!RENDERED_THIS_FRAME.contains(key)) {
            return -1;
        }
        Target holder = TARGETS.get(key);
        return holder == null ? -1 : holder.target.getColorTextureId();
    }

    public static void onRenderLevelStart(GameRenderer gameRenderer, float partialTick, long nanos) {
        int depth = passDepth;
        if (depth == 0) {
            frame++;
            RENDERED_LAST_FRAME.clear();
            for (PassKey rendered : RENDERED_THIS_FRAME) {
                if (rendered.parent() == null) {
                    RENDERED_LAST_FRAME.add(rendered.pane());
                }
            }
            RENDERED_THIS_FRAME.clear();
            passesThisFrame = 0;
            pendingTopLevelPasses = 0;
            chunkBuildBudget = CHUNK_BUILD_BUDGET_PER_FRAME;
            expireStaleTargets();
        }
        if (depth >= maxDepth()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }
        if (!VIEWS_ENABLED) {
            return;
        }
        if (Minecraft.useShaderTransparency() && !FABULOUS_VIEWS) {
            return;
        }
        if (PortalShaders.shader() == null) {
            return;
        }
        if (!resolveRenderChunkInfoCtor()) {
            return;
        }
        if (passesThisFrame >= MAX_PASSES_PER_FRAME) {
            return;
        }
        if (RemoteLevelManager.isRemotePassActive()) {
            return;
        }

        List<SelectedView> views = selectViews(mc, partialTick, depth);
        if (views.isEmpty()) {
            return;
        }
        if (depth == 0) {
            pendingTopLevelPasses = views.size();
        }

        if (depth == 0) {
            com.lowdragmc.photon.client.postprocessing.BloomEffect.updateScreenSize();
        }

        RenderTarget parentTarget = mc.getMainRenderTarget();
        PortalPassTrace.strategy(IrisCompat.isShaderPackActive());
        PortalFold parentFold = activeTransform;
        PortalWindow parentClipWindow = activeClipWindow;
        ViewKey parentDestinationPane = activeDestinationPane;
        PassKey parentPassKey = activePassKey;

        MinecraftPortalAccessor mcAccess = (MinecraftPortalAccessor) mc;
        GameRendererAccessor grAccess = (GameRendererAccessor) gameRenderer;
        boolean handWasRendered = grAccess.hexwright$getRenderHand();

        for (SelectedView view : views) {
            if (depth == 0) {
                pendingTopLevelPasses--;
            }
            if (passesThisFrame >= MAX_PASSES_PER_FRAME) {
                break;
            }
            PassKey passKey = new PassKey(view.key(), parentPassKey);
            Target holder = TARGETS.computeIfAbsent(passKey, key -> {
                Target created = new Target();
                created.target = new TextureTarget(parentTarget.width, parentTarget.height, true,
                    Minecraft.ON_OSX);
                created.target.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                return created;
            });
            if (holder.target.width != parentTarget.width || holder.target.height != parentTarget.height) {
                holder.target.resize(parentTarget.width, parentTarget.height, Minecraft.ON_OSX);
            }
            holder.lastUsedFrame = frame;

            if (view.remoteDimension() != null) {
                Entity viewEntity = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
                Vec3 foldedEye = view.fold().apply(viewEntity.getEyePosition(partialTick));
                if (!RemoteLevelManager.beginPass(view.remoteDimension(), foldedEye, view.remoteAnchor())) {
                    continue;
                }
            }
            activeTransform = view.fold();
            activeClipWindow = view.fold().toWindow();
            activeDestinationPane = new ViewKey(view.key().pairId(), 1 - view.key().side());
            activePassKey = passKey;
            passDepth = depth + 1;
            passesThisFrame++;
            grAccess.hexwright$setRenderHand(false);
            mcAccess.hexwright$setMainRenderTarget(holder.target);
            repointPhotonBloom(holder.target);
            markLightTextureDirty(gameRenderer);
            try {
                holder.target.clear(Minecraft.ON_OSX);
                holder.target.bindWrite(true);
                PortalPassTrace.probe("before renderLevel", holder.target, parentTarget);
                IrisCompat.beginNestedWorldPass();
                gameRenderer.renderLevel(partialTick, nanos, new PoseStack());
                IrisCompat.endNestedWorldPass();
                PortalPassTrace.probe("after renderLevel", holder.target, parentTarget);
                RENDERED_THIS_FRAME.add(passKey);
            } finally {
                endPortalPass();
                mcAccess.hexwright$setMainRenderTarget(parentTarget);
                repointPhotonBloom(parentTarget);
                grAccess.hexwright$setRenderHand(handWasRendered);
                passDepth = depth;
                activeTransform = parentFold;
                activeClipWindow = parentClipWindow;
                activeDestinationPane = parentDestinationPane;
                activePassKey = parentPassKey;
                activeCone = null;
                arrivalBox = null;
                RemoteLevelManager.endPass();
            }
        }

        markLightTextureDirty(gameRenderer);
        parentTarget.bindWrite(true);
        if (depth == 0) {
            Entity cameraEntity = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
            gameRenderer.getMainCamera().setup(mc.level, cameraEntity,
                !mc.options.getCameraType().isFirstPerson(),
                mc.options.getCameraType().isMirrored(), partialTick);
        }
    }

    private static void markLightTextureDirty(GameRenderer gameRenderer) {
        ((com.bluup.hexwright.mixin.LightTextureAccessor) gameRenderer.lightTexture())
            .hexwright$setUpdatePending(true);
    }

    private record SelectedView(ViewKey key, PortalFold fold, double distanceSq,
                                @Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> remoteDimension,
                                Vec3 remoteAnchor) {
    }

    private static double halfDiagonalFov(Minecraft mc) {
        double tanY = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5);
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double tanX = tanY * aspect;
        return Math.atan(Math.sqrt(tanX * tanX + tanY * tanY));
    }

    private static List<SelectedView> selectViews(Minecraft mc, float partialTick, int depth) {
        List<ClientPortalManager.Entry> entries = ClientPortalManager.entries();
        if (entries.isEmpty()) {
            return List.of();
        }
        boolean nested = depth > 0;
        PortalFold parentFold = activeTransform;
        Entity cameraEntity = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
        Vec3 eye = cameraEntity.getEyePosition(partialTick);
        Vec3 look = cameraEntity.getViewVector(partialTick);
        if (nested && parentFold != null) {
            eye = parentFold.apply(eye);
            look = parentFold.applyDirection(look);
        }
        double viewDistance = nested ? NESTED_VIEW_DISTANCE : VIEW_DISTANCE;
        double minSolidAngle = nested ? NESTED_MIN_SOLID_ANGLE : MIN_SOLID_ANGLE;
        ViewKey excluded = nested ? activeDestinationPane : null;
        double offScreenAngle = halfDiagonalFov(mc) + OFF_SCREEN_MARGIN;

        var currentDimension = mc.level.dimension();
        List<SelectedView> candidates = new ArrayList<>();
        for (ClientPortalManager.Entry entry : entries) {
            if (ClientPortalManager.openProgress(entry, partialTick) <= 0.0f) {
                continue;
            }
            PortalPair pair = entry.pair();
            for (int side = 0; side < 2; side++) {
                if (!pair.sideIn(side, currentDimension)) {
                    continue;
                }
                if (excluded != null && excluded.pairId().equals(pair.id()) && excluded.side() == side) {
                    continue;
                }
                PortalWindow window = pair.window(side);
                Vec3 center = window.center();
                double distSq = center.distanceToSqr(eye);
                if (distSq > viewDistance * viewDistance) {
                    continue;
                }
                double windowRadius = Math.max(window.width(), window.height()) * 0.5 + 1.0;
                if (nested || distSq > windowRadius * windowRadius) {
                    Vec3 toWindow = center.subtract(eye).normalize();
                    double angleToCenter = Math.acos(Mth.clamp(toWindow.dot(look), -1.0, 1.0));
                    double paneRadius = Math.hypot(window.width(), window.height()) * 0.5;
                    if (angleToCenter - Math.atan2(paneRadius, Math.sqrt(distSq)) > offScreenAngle) {
                        continue;
                    }
                    double solidAngle = window.width() * window.height()
                        * Math.abs(toWindow.dot(window.normal())) / distSq;
                    if (solidAngle < minSolidAngle) {
                        continue;
                    }
                }
                var destDimension = pair.dimensionOr(1 - side, currentDimension);
                boolean remote = !destDimension.equals(currentDimension);
                Vec3 destCenter = pair.window(1 - side).center();
                if (remote && (nested || !RemoteLevelManager.isReady(destDimension, destCenter))) {
                    continue;
                }
                PortalFold fold = parentFold == null || !nested
                    ? PortalFold.of(pair.transformFrom(side))
                    : parentFold.then(pair.transformFrom(side));
                candidates.add(new SelectedView(
                    new ViewKey(pair.id(), side), fold, distSq,
                    remote ? destDimension : null, destCenter));
            }
        }
        candidates.sort((a, b) -> Double.compare(sortDepth(a, nested), sortDepth(b, nested)));
        int spare = MAX_PASSES_PER_FRAME - passesThisFrame - (nested ? pendingTopLevelPasses : 0);
        int limit = Math.min(nested ? MAX_NESTED_VIEWS : MAX_VIEWS, spare);
        return candidates.size() > limit ? candidates.subList(0, Math.max(limit, 0)) : candidates;
    }

    private static double sortDepth(SelectedView candidate, boolean nested) {
        if (nested || !RENDERED_LAST_FRAME.contains(candidate.key())) {
            return candidate.distanceSq();
        }
        return candidate.distanceSq() * INCUMBENT_BIAS;
    }


    public static Matrix4f beginPortalPass(Matrix4f projection) {
        if (passDepth == 0 || activeClipWindow == null) {
            return projection;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        activeCone = CONE_ENABLED ? PortalCone.of(eye, activeClipWindow, farCullDistance(eye)) : null;
        arrivalBox = arrivalPrebuildBox(eye, activeClipWindow);
        applyScissor(camera, projection);
        return applyPortalClip(camera, projection);
    }

    private static @Nullable AABB arrivalPrebuildBox(Vec3 eye, PortalWindow window) {
        if (Math.abs(window.signedDistance(eye)) > ARRIVAL_PREBUILD_DISTANCE) {
            return null;
        }
        return AABB.ofSize(window.center(), ARRIVAL_PREBUILD_RANGE * 2.0,
            ARRIVAL_PREBUILD_RANGE * 2.0, ARRIVAL_PREBUILD_RANGE * 2.0);
    }

    private static void repointPhotonBloom(RenderTarget target) {
        repointBloomInput(BloomEffectAccessor.hexwright$getInput(), target);
        repointBloomInput(BloomEffectAccessor.hexwright$getTranslucentInput(), target);
        target.bindWrite(false);
    }

    public static void ensureBloomPointsAtMain() {
        if (passDepth > 0) {
            return;
        }
        if (BloomEffectAccessor.hexwright$getInput() == null
            && BloomEffectAccessor.hexwright$getTranslucentInput() == null) {
            return;
        }
        repointPhotonBloom(Minecraft.getInstance().getMainRenderTarget());
    }

    private static void repointBloomInput(@Nullable RenderTarget bloomInput, RenderTarget target) {
        if (bloomInput == null) {
            return;
        }
        BloomEffectAccessor.hexwright$hookColorBuffer(
            bloomInput, target.getColorTextureId(), GL30.GL_COLOR_ATTACHMENT0);
        BloomEffectAccessor.hexwright$hookDepthBuffer(bloomInput, target.getDepthTextureId());
        if (!bloomRepointWarned) {
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                bloomRepointWarned = true;
                com.bluup.hexwright.Hexwright.LOGGER.error(
                    "Photon bloom input framebuffer incomplete after portal repoint (status 0x{});"
                        + " bloom effects will be missing from portal views",
                    Integer.toHexString(status));
            }
        }
    }

    private static boolean bloomRepointWarned;

    public static void debugBloomAttachment() {
        if (!DEBUG || passDepth == 0 || bloomProbesLogged >= 3) {
            return;
        }
        RenderTarget bloomInput = BloomEffectAccessor.hexwright$getInput();
        if (bloomInput == null) {
            return;
        }
        int previous = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(
            GL30.GL_FRAMEBUFFER, bloomInput.frameBufferId);
        int attached = GL30.glGetFramebufferAttachmentParameteri(
            GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);

        RenderTarget current = Minecraft.getInstance().getMainRenderTarget();
        bloomProbesLogged++;
        com.bluup.hexwright.Hexwright.LOGGER.info(
            "[bloom-probe] in portal pass: INPUT colour attachment = {}, pane target colour = {} -> {}",
            attached, current.getColorTextureId(),
            attached == current.getColorTextureId() ? "MATCH (attachments are correct)"
                : "MISMATCH (something re-hooked it inside the pass)");
    }

    private static int bloomProbesLogged;

    public static void endPortalPass() {
        if (scissorActive) {
            RenderSystem.disableScissor();
            scissorActive = false;
        }
    }

    public static void ensureScissorClear() {
        RenderSystem.disableScissor();
        scissorActive = false;
    }

    public static void suspendScissor() {
        if (scissorActive) {
            RenderSystem.disableScissor();
        }
    }

    public static void resumeScissor() {
        if (scissorActive) {
            RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        }
    }

    private static double farCullDistance(Vec3 foldedEye) {
        Minecraft mc = Minecraft.getInstance();
        double halfExtent = (mc.options.getEffectiveRenderDistance() + 2) * 16.0;
        Vec3 centre = mc.player == null ? foldedEye : mc.player.position();
        double height = mc.level == null ? 512.0 : mc.level.getHeight() + 64.0;
        double dx = Math.abs(foldedEye.x - centre.x) + halfExtent;
        double dy = Math.abs(foldedEye.y - centre.y) + height;
        double dz = Math.abs(foldedEye.z - centre.z) + halfExtent;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void applyScissor(Camera camera, Matrix4f projection) {
        if (!SCISSOR_ENABLED) {
            return;
        }
        PortalWindow window = activeClipWindow;
        if (window == null) {
            return;
        }
        Vec3 camPos = camera.getPosition();
        if (Math.abs(window.signedDistance(camPos)) < MIN_SCISSOR_DEPTH) {
            return;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        int width = target.width;
        int height = target.height;
        if (width <= 0 || height <= 0) {
            return;
        }

        Matrix4f viewProjection = new Matrix4f(projection)
            .rotateX((float) Math.toRadians(camera.getXRot()))
            .rotateY((float) Math.toRadians(camera.getYRot() + 180.0f));

        double w = window.width();
        double h = window.height();
        double[][] corners = {{0.0, 0.0}, {w, 0.0}, {w, h}, {0.0, h}};
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        Vector4f clip = new Vector4f();
        for (double[] corner : corners) {
            Vec3 point = window.pointAt(corner[0], corner[1]).subtract(camPos);
            clip.set((float) point.x, (float) point.y, (float) point.z, 1.0f);
            viewProjection.transform(clip);
            if (clip.w <= 1.0e-4f) {
                return;
            }
            float screenX = (clip.x / clip.w * 0.5f + 0.5f) * width;
            float screenY = (clip.y / clip.w * 0.5f + 0.5f) * height;
            minX = Math.min(minX, screenX);
            minY = Math.min(minY, screenY);
            maxX = Math.max(maxX, screenX);
            maxY = Math.max(maxY, screenY);
        }

        int x0 = clampToScreen(Math.floor(minX) - SCISSOR_MARGIN, width);
        int y0 = clampToScreen(Math.floor(minY) - SCISSOR_MARGIN, height);
        int x1 = clampToScreen(Math.ceil(maxX) + SCISSOR_MARGIN, width);
        int y1 = clampToScreen(Math.ceil(maxY) + SCISSOR_MARGIN, height);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        if (x0 == 0 && y0 == 0 && x1 == width && y1 == height) {
            return;
        }
        scissorX = x0;
        scissorY = y0;
        scissorWidth = x1 - x0;
        scissorHeight = y1 - y0;
        RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        scissorActive = true;
    }

    private static int clampToScreen(double value, int limit) {
        if (!(value > 0.0)) {
            return 0;
        }
        return value >= limit ? limit : (int) value;
    }

    private static Matrix4f applyPortalClip(Camera camera, Matrix4f projection) {
        Vec3 camPos = camera.getPosition();
        PortalWindow window = activeClipWindow;
        if (window == null) {
            return projection;
        }

        double signedDist = window.signedDistance(camPos);
        double sign = signedDist >= 0.0 ? 1.0 : -1.0;
        Vec3 m = window.normal().scale(-sign);
        double depth = Math.max(Math.abs(signedDist) - CLIP_BIAS, MIN_CLIP_DEPTH);

        Matrix3f viewRotation = new Matrix3f()
            .rotationX((float) Math.toRadians(camera.getXRot()))
            .rotateY((float) Math.toRadians(camera.getYRot() + 180.0f));
        Vector3f normalView = viewRotation.transform(
            new Vector3f((float) m.x, (float) m.y, (float) m.z));
        float distView = (float) -depth;

        Matrix4f oblique = new Matrix4f(projection);
        Vector4f clip = new Vector4f(normalView.x, normalView.y, normalView.z, distView);
        Vector4f q = new Vector4f(Math.signum(clip.x), Math.signum(clip.y), 1.0f, 1.0f);
        new Matrix4f(oblique).invert().transform(q);
        float scale = 2.0f / clip.dot(q);
        oblique.m02(clip.x * scale - oblique.m03());
        oblique.m12(clip.y * scale - oblique.m13());
        oblique.m22(clip.z * scale - oblique.m23());
        oblique.m32(clip.w * scale - oblique.m33());
        return oblique;
    }


    public static void markFreshCrossing(Vec3 arrival) {
        freshPhase = FRESH_FILLING;
        freshCrossingFrames = 0;
        freshArrival = arrival;
        freshNearFieldBuilt = false;
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        if (levelRenderer != null) {
            levelRenderer.needsUpdate();
        }
    }

    public static boolean consumeFreshCrossingFrame() {
        if (!FRESH_FILL_ENABLED) {
            if (freshPhase != FRESH_OFF) {
                endFreshCrossing();
            }
            return false;
        }
        if (freshPhase == FRESH_OFF) {
            freshFillThisFrame = false;
            return false;
        }
        if (++freshCrossingFrames > MAX_FRESH_CROSSING_FRAMES) {
            endFreshCrossing();
            return false;
        }
        boolean graphCurrent = isVisibilityGraphCurrent();
        switch (freshPhase) {
            case FRESH_FILLING -> {
                Vec3 camPos = unfoldedCameraPos;
                boolean leftTheArea = freshArrival != null && camPos != null
                    && camPos.distanceToSqr(freshArrival) > STAND_DOWN_RANGE * STAND_DOWN_RANGE;
                if (graphCurrent && (freshNearFieldBuilt || leftTheArea)) {
                    LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
                    if (levelRenderer != null) {
                        levelRenderer.needsUpdate();
                    }
                    freshPhase = FRESH_FINAL_REBUILD;
                }
            }
            case FRESH_FINAL_REBUILD -> {
                if (graphCurrent) {
                    freshPhase = FRESH_GRACE;
                }
            }
            default -> {
                endFreshCrossing();
                return false;
            }
        }
        freshFillThisFrame = true;
        return true;
    }

    private static void endFreshCrossing() {
        freshPhase = FRESH_OFF;
        freshArrival = null;
        freshFillThisFrame = false;
    }

    public static boolean isFreshCrossingCompileActive() {
        return freshFillThisFrame && passDepth == 0;
    }

    private static boolean isVisibilityGraphCurrent() {
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        if (levelRenderer == null) {
            return true;
        }
        LevelRendererAccessor access = (LevelRendererAccessor) levelRenderer;
        if (access.hexwright$needsFullRenderChunkUpdate()) {
            return false;
        }
        Future<?> pending = access.hexwright$lastFullRenderChunkUpdate();
        if (pending != null && !pending.isDone()) {
            return false;
        }
        return access.hexwright$nextFullUpdateMillis().get() == 0L;
    }


    public static void fillPortalVisibility(ObjectArrayList<Object> out,
                                            ChunkRenderDispatcher.RenderChunk[] sections,
                                            Camera camera, Frustum frustum) {
        out.clear();
        if (renderChunkInfoCtor == null) {
            return;
        }
        boolean portalPass = passDepth > 0;
        Vec3 camPos = camera.getPosition();
        PortalCone cone = activeCone;
        AABB prebuild = arrivalBox;
        if (sortScratch.length < sections.length) {
            sortScratch = new long[sections.length];
        }
        long[] keys = sortScratch;
        int count = 0;
        for (int i = 0; i < sections.length; i++) {
            AABB bounds = sections[i].getBoundingBox();
            if (portalPass) {
                boolean drawable = cone == null || !cone.isOutside(bounds.minX, bounds.minY,
                    bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
                if (!drawable && (prebuild == null || !prebuild.intersects(bounds))) {
                    continue;
                }
            }
            double dx = (bounds.minX + bounds.maxX) * 0.5 - camPos.x;
            double dy = (bounds.minY + bounds.maxY) * 0.5 - camPos.y;
            double dz = (bounds.minZ + bounds.maxZ) * 0.5 - camPos.z;
            long distanceBits = Float.floatToRawIntBits((float) (dx * dx + dy * dy + dz * dz));
            keys[count++] = (distanceBits << 32) | i;
        }
        Arrays.sort(keys, 0, count);
        PENDING_BUILDS.clear();
        LevelLightEngine lighting = Minecraft.getInstance().level == null
            ? null : Minecraft.getInstance().level.getLightEngine();
        boolean nearBuilt = true;
        try {
            for (int i = 0; i < count; i++) {
                ChunkRenderDispatcher.RenderChunk section = sections[(int) (keys[i] & 0xFFFFFFFFL)];
                AABB bounds = section.getBoundingBox();
                if (portalPass) {
                    PENDING_BUILDS.add(section);
                    boolean drawable = cone == null || !cone.isOutside(bounds.minX, bounds.minY,
                        bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
                    if (drawable && frustum.isVisible(bounds)) {
                        out.add(renderChunkInfoCtor.invoke(section, (Direction) null, 0));
                    }
                    continue;
                }
                if (!frustum.isVisible(bounds)) {
                    continue;
                }
                out.add(renderChunkInfoCtor.invoke(section, (Direction) null, 0));
                float camDistSq = Float.intBitsToFloat((int) (keys[i] >>> 32));
                if (camDistSq <= FRESH_BUILD_RANGE * FRESH_BUILD_RANGE) {
                    PENDING_BUILDS.add(section);
                }
                if (nearBuilt
                    && camDistSq <= NEAR_COMPLETE_RANGE * NEAR_COMPLETE_RANGE
                    && (section.isDirty()
                        || section.getCompiledChunk() == ChunkRenderDispatcher.CompiledChunk.UNCOMPILED)
                    && lighting != null
                    && lighting.lightOnInSection(SectionPos.of(section.getOrigin()))) {
                    nearBuilt = false;
                }
            }
            if (!portalPass) {
                freshNearFieldBuilt = nearBuilt;
            }
        } catch (Throwable t) {
            out.clear();
            PENDING_BUILDS.clear();
            renderChunkInfoCtor = null;
            renderChunkInfoBroken = true;
            com.bluup.hexwright.Hexwright.LOGGER.error(
                "Portal visibility fill failed; disabling portal views", t);
        }
    }

    private static long[] sortScratch = new long[0];


    private static final int CHUNK_BUILD_BUDGET_PER_FRAME = 12;

    private static final int CHUNK_BUILD_BUDGET_BUSY = 2;

    private static final int CHUNK_BUILD_QUEUE_HEADROOM = 8;

    private static final double ARRIVAL_PREBUILD_DISTANCE = 24.0;

    private static final double ARRIVAL_PREBUILD_RANGE = 48.0;

    private static final double FRESH_BUILD_RANGE = 96.0;

    private static final double NEAR_COMPLETE_RANGE = 48.0;

    private static final double STAND_DOWN_RANGE = 64.0;

    private static final int FRESH_CROSSING_BUILD_BUDGET = 32;

    private static final List<ChunkRenderDispatcher.RenderChunk> PENDING_BUILDS = new ArrayList<>();

    private static int chunkBuildBudget;

    public static void compilePortalChunks(ChunkRenderDispatcher dispatcher) {
        Minecraft mc = Minecraft.getInstance();
        if (dispatcher == null || mc.level == null || chunkBuildBudget <= 0) {
            return;
        }
        int spend = dispatcher.getToBatchCount() > CHUNK_BUILD_QUEUE_HEADROOM
            ? CHUNK_BUILD_BUDGET_BUSY : CHUNK_BUILD_BUDGET_PER_FRAME;
        LevelLightEngine lighting = mc.level.getLightEngine();
        RenderRegionCache cache = null;
        for (ChunkRenderDispatcher.RenderChunk section : PENDING_BUILDS) {
            if (chunkBuildBudget <= 0 || spend <= 0) {
                break;
            }
            if (!section.isDirty() || !lighting.lightOnInSection(SectionPos.of(section.getOrigin()))) {
                continue;
            }
            if (cache == null) {
                cache = new RenderRegionCache();
            }
            section.rebuildChunkAsync(dispatcher, cache);
            section.setNotDirty();
            chunkBuildBudget--;
            spend--;
        }
        dispatcher.uploadAllPendingUploads();
    }

    public static void compileFreshCrossingChunks(ChunkRenderDispatcher dispatcher) {
        Minecraft mc = Minecraft.getInstance();
        if (dispatcher == null || mc.level == null) {
            return;
        }
        LevelLightEngine lighting = mc.level.getLightEngine();
        RenderRegionCache cache = null;
        int spend = FRESH_CROSSING_BUILD_BUDGET;
        for (ChunkRenderDispatcher.RenderChunk section : PENDING_BUILDS) {
            if (spend <= 0) {
                break;
            }
            if (!section.isDirty() || !lighting.lightOnInSection(SectionPos.of(section.getOrigin()))) {
                continue;
            }
            if (cache == null) {
                cache = new RenderRegionCache();
            }
            section.rebuildChunkAsync(dispatcher, cache);
            section.setNotDirty();
            spend--;
        }
        dispatcher.uploadAllPendingUploads();
    }

    private static @Nullable MethodHandle renderChunkInfoCtor;
    private static boolean renderChunkInfoBroken;

    private static boolean resolveRenderChunkInfoCtor() {
        if (renderChunkInfoCtor != null) {
            return true;
        }
        if (renderChunkInfoBroken) {
            return false;
        }
        try {
            for (Class<?> inner : LevelRenderer.class.getDeclaredClasses()) {
                for (Constructor<?> candidate : inner.getDeclaredConstructors()) {
                    Class<?>[] params = candidate.getParameterTypes();
                    if (params.length == 3
                        && params[0] == ChunkRenderDispatcher.RenderChunk.class
                        && params[1] == Direction.class
                        && params[2] == int.class) {
                        candidate.setAccessible(true);
                        renderChunkInfoCtor = MethodHandles.lookup().unreflectConstructor(candidate);
                        return true;
                    }
                }
            }
            throw new NoSuchMethodException("no (RenderChunk, Direction, int) constructor in LevelRenderer's inner classes");
        } catch (ReflectiveOperationException | SecurityException e) {
            renderChunkInfoBroken = true;
            com.bluup.hexwright.Hexwright.LOGGER.error(
                "Could not resolve RenderChunkInfo constructor; portal views disabled", e);
            return false;
        }
    }


    public static void pruneTargets() {
        Set<UUID> live = new HashSet<>();
        for (ClientPortalManager.Entry entry : ClientPortalManager.entries()) {
            live.add(entry.pair().id());
        }
        TARGETS.entrySet().removeIf(mapping -> {
            if (mapping.getKey().mentionsDeadPair(live)) {
                mapping.getValue().target.destroyBuffers();
                return true;
            }
            return false;
        });
    }

    public static void destroyAllTargets() {
        for (Target holder : TARGETS.values()) {
            holder.target.destroyBuffers();
        }
        TARGETS.clear();
        RENDERED_THIS_FRAME.clear();
        RENDERED_LAST_FRAME.clear();
    }

    private static void expireStaleTargets() {
        TARGETS.entrySet().removeIf(mapping -> {
            if (frame - mapping.getValue().lastUsedFrame > TARGET_TTL_FRAMES) {
                mapping.getValue().target.destroyBuffers();
                return true;
            }
            return false;
        });
        while (TARGETS.size() > MAX_TARGETS) {
            Map.Entry<PassKey, Target> oldest = null;
            for (Map.Entry<PassKey, Target> candidate : TARGETS.entrySet()) {
                if (oldest == null || candidate.getValue().lastUsedFrame < oldest.getValue().lastUsedFrame) {
                    oldest = candidate;
                }
            }
            if (oldest == null) {
                break;
            }
            oldest.getValue().target.destroyBuffers();
            TARGETS.remove(oldest.getKey());
        }
    }
}
