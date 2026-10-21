package com.bluup.hexwright.client.bindstone;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.server.bindstone.BindstonePillar;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public final class BindstoneDissolveRenderer {

    private static final RandomSource RANDOM = RandomSource.create();

    private static final List<Ghost> GHOSTS = new ArrayList<>();

    private BindstoneDissolveRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(BindstoneDissolveRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> age());
    }

    public static void start(BlockPos origin, int ticks) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || ticks <= 0) {
            return;
        }
        GHOSTS.removeIf(ghost -> ghost.origin.equals(origin));

        BlockRenderDispatcher blocks = client.getBlockRenderer();
        List<Piece> pieces = new ArrayList<>();
        BindstonePillar.forEachBlock(origin, (pos, built) -> {
            if (pos.equals(origin)) {
                return;
            }
            BlockState live = level.getBlockState(pos);
            BlockState state = BindstonePillar.isPillarBlock(live) ? live : built;
            pieces.add(new Piece(pos.immutable(), state, blocks.getBlockModel(state), faceLight(level, pos)));
        });
        if (pieces.isEmpty()) {
            return;
        }
        GHOSTS.add(new Ghost(origin.immutable(), ticks, pieces));
        HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] dissolving the pillar at {} over {} ticks: {} blocks",
            origin.toShortString(), ticks, pieces.size());
    }

    private static void age() {
        GHOSTS.removeIf(ghost -> ++ghost.age > ghost.ticks);
    }

    private static void render(WorldRenderContext context) {
        if (GHOSTS.isEmpty() || IrisCompat.isRenderingShadowPass() || PortalViewRenderer.isRenderingView()) {
            return;
        }
        ShaderInstance shader = BindstoneDissolveShaders.shader();
        if (shader == null) {
            return;
        }

        Vec3 camera = context.camera().getPosition();
        float partialTick = context.tickDelta();

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(context.matrixStack().last().pose());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        try {
            for (Ghost ghost : GHOSTS) {
                draw(shader, ghost, camera, partialTick);
            }
        } finally {
            RenderSystem.enableCull();
            Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        }
    }

    private static void draw(ShaderInstance shader, Ghost ghost, Vec3 camera, float partialTick) {
        float progress = Math.min((ghost.age + partialTick) / ghost.ticks, 1.0f);

        shader.safeGetUniform("Origin").set(
            (float) (ghost.origin.getX() - camera.x),
            (float) (ghost.origin.getY() - camera.y),
            (float) (ghost.origin.getZ() - camera.z));
        shader.safeGetUniform("Dissolve").set(progress, SWEEP, RIM_WIDTH, ghost.seed);
        shader.safeGetUniform("Shape").set((float) (BindstonePillar.RINGS + 1), NOISE_SCALE, NOISE_DETAIL, HEAT);
        shader.safeGetUniform("Energy").set(ENERGY_R, ENERGY_G, ENERGY_B, ENERGY_BOOST);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        PoseStack poses = new PoseStack();
        for (Piece piece : ghost.pieces) {
            poses.pushPose();
            poses.translate(piece.pos.getX() - camera.x, piece.pos.getY() - camera.y, piece.pos.getZ() - camera.z);
            emit(builder, poses.last(), piece);
            poses.popPose();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void emit(BufferBuilder builder, PoseStack.Pose pose, Piece piece) {
        for (Direction side : Direction.values()) {
            put(builder, pose, piece.model.getQuads(piece.state, side, RANDOM), piece.light[side.ordinal()]);
        }
        put(builder, pose, piece.model.getQuads(piece.state, null, RANDOM), piece.light[BRIGHTEST]);
    }

    private static void put(BufferBuilder builder, PoseStack.Pose pose, List<BakedQuad> quads, int light) {
        for (BakedQuad quad : quads) {
            builder.putBulkData(pose, quad, 1.0f, 1.0f, 1.0f, light, OverlayTexture.NO_OVERLAY);
        }
    }

    private static int[] faceLight(ClientLevel level, BlockPos pos) {
        int[] light = new int[BRIGHTEST + 1];
        for (Direction side : Direction.values()) {
            int packed = LevelRenderer.getLightColor(level, pos.relative(side));
            light[side.ordinal()] = packed;
            light[BRIGHTEST] = Math.max(light[BRIGHTEST], packed);
        }
        return light;
    }

    private static final float SWEEP = 1.0f;

    private static final float RIM_WIDTH = 0.18f;

    private static final float NOISE_SCALE = 2.6f;
    private static final float NOISE_DETAIL = 2.0f;

    private static final float HEAT = 0.45f;

    private static final float ENERGY_R = 0.298f;
    private static final float ENERGY_G = 0.839f;
    private static final float ENERGY_B = 1.0f;
    private static final float ENERGY_BOOST = 2.6f;

    private static final class Ghost {
        private final BlockPos origin;
        private final int ticks;
        private final List<Piece> pieces;
        private final float seed;
        private int age;

        private Ghost(BlockPos origin, int ticks, List<Piece> pieces) {
            this.origin = origin;
            this.ticks = ticks;
            this.pieces = pieces;
            this.seed = Math.abs(origin.hashCode() % 997) * 0.37f;
        }
    }

    private static final int BRIGHTEST = 6;

    private record Piece(BlockPos pos, BlockState state, BakedModel model, int[] light) {
    }
}
