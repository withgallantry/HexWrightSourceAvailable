package com.bluup.hexwright.client.dust;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.render.IrisCompat;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public final class DustConstructRenderer implements SimpleSynchronousResourceReloadListener {

    private static final ResourceLocation ID = Hexwright.id("dust_construct_shaders");
    private static final String SHADER_NAME = "hexwright_dust_construct";
    private static final int FIELD_UNIT = 7;
    private static final int NOISE_UNIT = 6;
    private static final float PROXY_PAD = 0.3f;

    private static final float[] COLOR_DARK = {0.42f, 0.31f, 0.49f};
    private static final float[] COLOR_LIGHT = {0.87f, 0.75f, 0.91f};
    private static final float[] COLOR_GLOW = {0.98f, 0.82f, 1.0f};

    private static @Nullable ShaderInstance shader;
    private static boolean failed;
    private static int boundProgram = -1;
    private static int fieldLocation = -1;
    private static int noiseLocation = -1;
    private static int impactsLocation = -1;
    private static final FloatBuffer IMPACTS = MemoryUtil.memAllocFloat(DustConstruct.MAX_IMPACTS * 8);
    private static final List<DustConstruct> VISIBLE = new ArrayList<>();

    private DustConstructRenderer() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new DustConstructRenderer());
        WorldRenderEvents.AFTER_ENTITIES.register(DustConstructRenderer::render);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        if (shader != null) {
            shader.close();
            shader = null;
        }
        boundProgram = -1;
        failed = false;
        try {
            shader = new ShaderInstance(resourceManager, SHADER_NAME, DefaultVertexFormat.POSITION);
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load the dust construct shader; formed dust will show as grains only", e);
            shader = null;
            failed = true;
        }
    }

    static boolean available() {
        return shader != null && !failed && DustConfig.constructEnabled != 0;
    }

    private static void render(WorldRenderContext context) {
        ShaderInstance program = shader;
        if (program == null || DustConfig.constructEnabled == 0) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass() || PortalViewRenderer.isRenderingView()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        VISIBLE.clear();
        DustClient.collectConstructs(VISIBLE);
        if (VISIBLE.isEmpty()) {
            return;
        }

        Vec3 camera = context.camera().getPosition();
        float partialTick = context.tickDelta();
        float m11 = RenderSystem.getProjectionMatrix().m11();
        float pixelAngle = 2.0f / (Math.max(Math.abs(m11), 1.0e-3f) * Math.max(mc.getWindow().getHeight(), 1));

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(context.matrixStack().last().pose());
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.setShader(() -> program);

            for (DustConstruct construct : VISIBLE) {
                draw(program, construct, level, camera, partialTick, pixelAngle);
            }
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void draw(ShaderInstance program, DustConstruct construct, ClientLevel level, Vec3 camera,
                             float partialTick, float pixelAngle) {
        RegionField field = construct.field;
        RegionField.Grid grid = field.grid;
        int texture = construct.texture();
        if (texture == 0) {
            return;
        }

        double cx = construct.prevX + (construct.x - construct.prevX) * partialTick;
        double cy = construct.prevY + (construct.y - construct.prevY) * partialTick;
        double cz = construct.prevZ + (construct.z - construct.prevZ) * partialTick;
        float ox = (float) (cx - camera.x);
        float oy = (float) (cy - camera.y);
        float oz = (float) (cz - camera.z);

        float stretch = construct.stretch();
        float reach = Math.max(Math.max(field.halfX, field.halfY), field.halfZ) * (stretch - 1.0f) * 1.5f;
        float pad = construct.outer() + DustConfig.constructRoughness * 2.0f + PROXY_PAD;
        float px = field.halfX + pad + reach * Math.abs(construct.travelX);
        float py = field.halfY + pad + reach * Math.abs(construct.travelY);
        float pz = field.halfZ + pad + reach * Math.abs(construct.travelZ);

        set(program, "Origin", ox, oy, oz);
        set(program, "GridMin", grid.minX, grid.minY, grid.minZ);
        set(program, "GridSpan", grid.cellX * (grid.nx - 1), grid.cellY * (grid.ny - 1), grid.cellZ * (grid.nz - 1));
        set(program, "GridRes", grid.nx, grid.ny, grid.nz);
        set(program, "ProxyMin", -px, -py, -pz);
        set(program, "ProxyMax", px, py, pz);
        program.safeGetUniform("Band").set(construct.bandCentre, construct.bandHalf,
            DustConfig.constructRoughness, DustConfig.constructGrainSize);
        program.safeGetUniform("Form").set(construct.coverage, construct.build,
            construct.dissolving ? 1.0f : 0.0f, construct.age + partialTick);
        program.safeGetUniform("FormDir").set(construct.dirX, construct.dirY, construct.dirZ, 0.0f);
        program.safeGetUniform("Motion").set(construct.travelX, construct.travelY, construct.travelZ, stretch);

        int impacts = 0;
        IMPACTS.clear();
        for (int i = 0; i < DustConstruct.MAX_IMPACTS; i++) {
            float intensity = construct.impactIntensity(i);
            if (intensity <= 0.001f) {
                continue;
            }
            IMPACTS.put(construct.impactX[i]).put(construct.impactY[i]).put(construct.impactZ[i])
                .put(construct.impactRadius[i]);
            IMPACTS.put(intensity).put(construct.impactAge[i] + partialTick).put(construct.impactRipple[i]).put(0.0f);
            impacts++;
        }
        IMPACTS.flip();
        program.safeGetUniform("Lighting").set(worldLight(level, cx, cy, cz, field.halfY, partialTick),
            DustConfig.constructGlow, pixelAngle, impacts);
        program.safeGetUniform("Life").set(DustConfig.constructSparkle,
            0.02f * DustConfig.constructLife, DustConfig.constructLife,
            Math.max(DustConfig.constructQuality, 0.15f));
        float quality = Math.max(DustConfig.constructQuality, 0.15f);
        program.safeGetUniform("Quality").set(Math.round(64 * quality), 0.9f, Math.round(10 * quality),
            0.03f / quality);
        set(program, "ColorDark", COLOR_DARK[0], COLOR_DARK[1], COLOR_DARK[2]);
        set(program, "ColorLight", COLOR_LIGHT[0], COLOR_LIGHT[1], COLOR_LIGHT[2]);
        set(program, "ColorGlow", COLOR_GLOW[0], COLOR_GLOW[1], COLOR_GLOW[2]);

        int id = program.getId();
        GlStateManager._glUseProgram(id);
        if (boundProgram != id) {
            boundProgram = id;
            fieldLocation = Uniform.glGetUniformLocation(id, "DustField");
            noiseLocation = Uniform.glGetUniformLocation(id, "NoiseTex");
            impactsLocation = Uniform.glGetUniformLocation(id, "Impacts");
        }
        if (fieldLocation >= 0) {
            GL20.glUniform1i(fieldLocation, FIELD_UNIT);
        }
        if (noiseLocation >= 0) {
            GL20.glUniform1i(noiseLocation, NOISE_UNIT);
        }
        if (impactsLocation >= 0 && impacts > 0) {
            GL20.glUniform4fv(impactsLocation, IMPACTS);
        }

        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + FIELD_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + NOISE_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, DustNoise.texture());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        box(builder, ox - px, oy - py, oz - pz, ox + px, oy + py, oz + pz);
        BufferUploader.drawWithShader(builder.end());

        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + FIELD_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + NOISE_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static float worldLight(ClientLevel level, double x, double y, double z, float halfY, float partialTick) {
        float sky = level.getSkyDarken(partialTick);
        float best = 0.0f;
        for (int i = 0; i < 2; i++) {
            BlockPos pos = BlockPos.containing(x, i == 0 ? y : y + halfY + 0.5, z);
            int packed = LevelRenderer.getLightColor(level, pos);
            float block = ((packed >> 4) & 0xF) / 15.0f;
            float skyLight = ((packed >> 20) & 0xF) / 15.0f * sky;
            best = Math.max(best, Math.max(block, skyLight));
        }
        return 0.3f + 0.7f * best;
    }

    private static void set(ShaderInstance program, String name, float x, float y, float z) {
        program.safeGetUniform(name).set(x, y, z);
    }

    private static void box(BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(b, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(b, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(b, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(b, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(b, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(b, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(BufferBuilder b, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        b.vertex(ax, ay, az).endVertex();
        b.vertex(bx, by, bz).endVertex();
        b.vertex(cx, cy, cz).endVertex();
        b.vertex(dx, dy, dz).endVertex();
    }
}
