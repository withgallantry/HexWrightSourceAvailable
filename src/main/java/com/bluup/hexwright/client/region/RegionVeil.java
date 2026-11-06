package com.bluup.hexwright.client.region;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.client.render.OpaqueDepthSnapshot;
import com.bluup.hexwright.client.render.SceneSnapshot;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionDistance;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Optional;

public final class RegionVeil implements SimpleSynchronousResourceReloadListener {

    private static final ResourceLocation ID = Hexwright.id("region_veil");
    private static final String SHADER_NAME = "hexwright_region_veil";
    private static final ResourceLocation LOOK_FILE = Hexwright.id("region_veil.json");
    private static final int NOISE_UNIT = 6;
    private static final int FIELD_UNIT = 7;
    private static final int NOISE_SIZE = 64;
    private static final int NOISE_CELLS = 8;
    private static final double REACH = 6.5;
    private static final int[][] GRADIENTS = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};

    private static @Nullable ShaderInstance shader;
    private static boolean failed;
    private static int noiseTexture;
    private static int boundProgram = -1;
    private static int noiseLocation = -1;
    private static int fieldLocation = -1;
    private static volatile Look look = Look.DEFAULT;
    private static @Nullable BlendMode previousBlend;

    public record Look(float intensity, float coverage, float spread, float veil, float currents, float interior,
                       float darkening, float rise, float swirl, Sizing thickness, Sizing scale, float strandWidth,
                       float energyPaling, int steps, long activationMs) {

        static final Look DEFAULT = new Look(1.0F, 0.3F, 2.2F, 0.55F, 1.6F, 1.0F, 0.35F, 0.2F, 0.05F,
            new Sizing(0.16F, 0.3F, 1.2F), new Sizing(0.5F, 1.2F, 3.0F), 0.07F, 0.45F, 96, 900L);

        public record Sizing(float perThickness, float min, float max) {

            float of(float thickness) {
                return Mth.clamp(thickness * perThickness, min, max);
            }

            static Sizing parse(JsonObject json, String name, Sizing fallback) {
                if (!json.has(name)) {
                    return fallback;
                }
                JsonObject sizing = GsonHelper.getAsJsonObject(json, name);
                return new Sizing(GsonHelper.getAsFloat(sizing, "per_thickness", fallback.perThickness),
                    GsonHelper.getAsFloat(sizing, "min", fallback.min), GsonHelper.getAsFloat(sizing, "max", fallback.max));
            }
        }

        static Look parse(JsonObject json) {
            Look d = DEFAULT;
            return new Look(GsonHelper.getAsFloat(json, "intensity", d.intensity),
                GsonHelper.getAsFloat(json, "coverage", d.coverage), GsonHelper.getAsFloat(json, "spread", d.spread),
                GsonHelper.getAsFloat(json, "veil", d.veil), GsonHelper.getAsFloat(json, "currents", d.currents),
                GsonHelper.getAsFloat(json, "interior", d.interior), GsonHelper.getAsFloat(json, "darkening", d.darkening),
                GsonHelper.getAsFloat(json, "rise", d.rise), GsonHelper.getAsFloat(json, "swirl", d.swirl),
                Sizing.parse(json, "thickness", d.thickness), Sizing.parse(json, "scale", d.scale),
                GsonHelper.getAsFloat(json, "strand_width", d.strandWidth),
                GsonHelper.getAsFloat(json, "energy_paling", d.energyPaling), GsonHelper.getAsInt(json, "steps", d.steps),
                GsonHelper.getAsLong(json, "activation_ms", d.activationMs));
        }

        public int energy(int colour) {
            int paled = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                int channel = (colour >> shift) & 0xFF;
                paled |= (channel + (int) ((255 - channel) * energyPaling)) << shift;
            }
            return paled;
        }
    }

    public record Moment(float activation, float charge, float gather, float flash, float fade) {
    }

    public static final class Shape implements AutoCloseable {

        private final RegionGrid grid;
        private final float thickness;
        private final float centreX;
        private final float centreY;
        private final float centreZ;
        private final float bottom;
        private final float top;
        private int texture;

        private Shape(RegionGrid grid, float thickness, float centreX, float centreY, float centreZ, float bottom,
                      float top) {
            this.grid = grid;
            this.thickness = thickness;
            this.centreX = centreX;
            this.centreY = centreY;
            this.centreZ = centreZ;
            this.bottom = bottom;
            this.top = top;
        }

        public static Shape trace(Region region) {
            RegionDistance.Measure measure = RegionDistance.measure(region);
            float thickness = measure.area() > 0.0 ? (float) (3.0 * measure.volume() / measure.area()) : 0.0F;
            RegionGrid grid = RegionGrid.sample(region, REACH * look.thickness().of(thickness));
            float[] surface = grid.surface();
            float bottom = Float.MAX_VALUE;
            float top = -Float.MAX_VALUE;
            for (int i = 1; i < surface.length; i += 3) {
                bottom = Math.min(bottom, surface[i]);
                top = Math.max(top, surface[i]);
            }
            if (surface.length == 0) {
                bottom = 0.0F;
                top = 0.0F;
            }
            Vec3 centre = region.bounds().getCenter();
            return new Shape(grid, thickness, (float) (centre.x - grid.originX()), (float) (centre.y - grid.originY()),
                (float) (centre.z - grid.originZ()), bottom, top);
        }

        public RegionGrid grid() {
            return grid;
        }

        public float bottom() {
            return bottom;
        }

        public float top() {
            return top;
        }

        private int texture() {
            if (texture != 0) {
                return texture;
            }
            float[] distance = grid.distance();
            FloatBuffer data = MemoryUtil.memAllocFloat(distance.length);
            try {
                data.put(distance).flip();
                texture = GL11.glGenTextures();
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
                resetUnpack(4);
                GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R32F, grid.nz(), grid.ny(), grid.nx(), 0,
                    GL11.GL_RED, GL11.GL_FLOAT, data);
                filter(GL12.GL_CLAMP_TO_EDGE);
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            } finally {
                MemoryUtil.memFree(data);
            }
            return texture;
        }

        @Override
        public void close() {
            if (texture != 0) {
                GL11.glDeleteTextures(texture);
                texture = 0;
            }
        }
    }

    private RegionVeil() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new RegionVeil());
    }

    public static Look look() {
        return look;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        look = loadLook(resourceManager);
        if (shader != null) {
            shader.close();
            shader = null;
        }
        boundProgram = -1;
        failed = false;
        try {
            shader = new ShaderInstance(resourceManager, SHADER_NAME, DefaultVertexFormat.POSITION);
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded the Region veil shader");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load the Region veil shader; Malediction warnings will not be drawn", e);
            shader = null;
            failed = true;
        }
        if (noiseTexture == 0 && !failed) {
            long started = System.nanoTime();
            noiseTexture = createNoise();
            HexwrightDebug.log(HexwrightDebug.RENDER, "[veil] noise volume made in {} ms",
                (System.nanoTime() - started) / 1_000_000L);
        }
    }

    private static Look loadLook(ResourceManager resourceManager) {
        Optional<Resource> resource = resourceManager.getResource(LOOK_FILE);
        if (resource.isEmpty()) {
            Hexwright.LOGGER.warn("{} is missing; Region veils use their built-in look", LOOK_FILE);
            return Look.DEFAULT;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return Look.parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.warn("{} could not be read; Region veils use their built-in look", LOOK_FILE, e);
            return Look.DEFAULT;
        }
    }

    public static void captureOpaqueDepth() {
        if (shader != null && !IrisCompat.isRenderingShadowPass() && !PortalViewRenderer.isRenderingView()) {
            OpaqueDepthSnapshot.capture();
        }
    }

    public static boolean begin(WorldRenderContext context) {
        ShaderInstance program = shader;
        if (program == null || noiseTexture == 0 || IrisCompat.isRenderingShadowPass()) {
            return false;
        }
        int depth = SceneSnapshot.sceneDepth(SceneSnapshot.SLOT_REGION_VEIL);
        int opaque = PortalViewRenderer.isRenderingView() ? 0 : OpaqueDepthSnapshot.depthTextureId();
        Minecraft mc = Minecraft.getInstance();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        program.setSampler("DepthSampler", depth);
        program.safeGetUniform("DepthReady").set(depth != 0 ? 1.0F : 0.0F);
        program.setSampler("OpaqueSampler", opaque);
        program.safeGetUniform("OpaqueReady").set(opaque != 0 ? 1.0F : 0.0F);
        program.safeGetUniform("InverseProjMat").set(new Matrix4f(projection).invert());
        program.safeGetUniform("PixelAngle").set(2.0F / (Math.max(Math.abs(projection.m11()), 1.0e-3F)
            * Math.max(mc.getWindow().getHeight(), 1)));
        program.safeGetUniform("Time").set((Util.getMillis() % 3_600_000L) / 1000.0F);
        Look current = look;
        program.safeGetUniform("Intensity").set(current.intensity());
        program.safeGetUniform("Coverage").set(current.coverage());
        program.safeGetUniform("Spread").set(current.spread());
        program.safeGetUniform("Veil").set(current.veil());
        program.safeGetUniform("Currents").set(current.currents());
        program.safeGetUniform("Interior").set(current.interior());
        program.safeGetUniform("Darkening").set(current.darkening());
        program.safeGetUniform("Rise").set(current.rise());
        program.safeGetUniform("Swirl").set(current.swirl());
        program.safeGetUniform("StrandWidth").set(current.strandWidth());
        program.safeGetUniform("Steps").set((float) current.steps());

        int id = program.getId();
        GlStateManager._glUseProgram(id);
        if (boundProgram != id) {
            boundProgram = id;
            noiseLocation = Uniform.glGetUniformLocation(id, "VeilNoise");
            fieldLocation = Uniform.glGetUniformLocation(id, "RegionField");
        }
        GL20.glUniform1i(noiseLocation, NOISE_UNIT);
        GL20.glUniform1i(fieldLocation, FIELD_UNIT);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + NOISE_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, noiseTexture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);

        previousBlend = BlendModeAccessor.hexwright$getLastApplied();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(context.matrixStack().last().pose());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (depth != 0 || opaque != 0) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }
        RenderSystem.setShader(() -> program);
        return true;
    }

    public static void draw(Shape shape, int colour, float seed, Moment moment, Vec3 camera) {
        ShaderInstance program = shader;
        if (program == null) {
            return;
        }
        Look current = look;
        RegionGrid grid = shape.grid();
        float ox = (float) (grid.originX() - camera.x);
        float oy = (float) (grid.originY() - camera.y);
        float oz = (float) (grid.originZ() - camera.z);
        float sx = (float) ((grid.nx() - 1) * grid.spacing());
        float sy = (float) ((grid.ny() - 1) * grid.spacing());
        float sz = (float) ((grid.nz() - 1) * grid.spacing());
        program.safeGetUniform("Origin").set(ox, oy, oz);
        program.safeGetUniform("GridSpan").set(sx, sy, sz);
        program.safeGetUniform("GridRes").set((float) grid.nx(), (float) grid.ny(), (float) grid.nz());
        program.safeGetUniform("Centre").set(shape.centreX, shape.centreY, shape.centreZ);
        program.safeGetUniform("Bottom").set(shape.bottom);
        program.safeGetUniform("Top").set(shape.top);
        program.safeGetUniform("Thickness").set(current.thickness().of(shape.thickness));
        program.safeGetUniform("Scale").set(current.scale().of(shape.thickness));
        program.safeGetUniform("Seed").set(seed);
        setColour(program, "BaseColour", colour);
        setColour(program, "EnergyColour", current.energy(colour));
        program.safeGetUniform("Activation").set(moment.activation());
        program.safeGetUniform("Charge").set(moment.charge());
        program.safeGetUniform("Gather").set(moment.gather());
        program.safeGetUniform("Flash").set(moment.flash());
        program.safeGetUniform("Fade").set(moment.fade());

        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + FIELD_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, shape.texture());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        box(builder, ox, oy, oz, ox + sx, oy + sy, oz + sz);
        BufferUploader.drawWithShader(builder.end());
    }

    public static void end() {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + FIELD_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + NOISE_UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        BlendModeAccessor.hexwright$setLastApplied(previousBlend);
        previousBlend = null;
    }

    private static void setColour(ShaderInstance program, String name, int colour) {
        program.safeGetUniform(name).set(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
            (colour & 0xFF) / 255.0F);
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

    private static int createNoise() {
        int[] gradient = new int[4 * NOISE_CELLS * NOISE_CELLS * NOISE_CELLS];
        for (int c = 0; c < 4; c++) {
            for (int x = 0; x < NOISE_CELLS; x++) {
                for (int y = 0; y < NOISE_CELLS; y++) {
                    for (int z = 0; z < NOISE_CELLS; z++) {
                        gradient[((c * NOISE_CELLS + x) * NOISE_CELLS + y) * NOISE_CELLS + z] =
                            (latticeHash(x, y, z, c) >>> 8) % GRADIENTS.length;
                    }
                }
            }
        }
        ByteBuffer data = MemoryUtil.memAlloc(NOISE_SIZE * NOISE_SIZE * NOISE_SIZE * 4);
        try {
            for (int z = 0; z < NOISE_SIZE; z++) {
                for (int y = 0; y < NOISE_SIZE; y++) {
                    for (int x = 0; x < NOISE_SIZE; x++) {
                        for (int c = 0; c < 4; c++) {
                            double value = gradientNoise(gradient, c, x, y, z);
                            data.put((byte) Mth.clamp((int) Math.round((0.5 + 0.5 * value) * 255.0), 0, 255));
                        }
                    }
                }
            }
            data.flip();
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
            resetUnpack(4);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, NOISE_SIZE, NOISE_SIZE, NOISE_SIZE, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
            filter(GL11.GL_REPEAT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            return texture;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static double gradientNoise(int[] gradient, int c, int x, int y, int z) {
        int texelsPerCell = NOISE_SIZE / NOISE_CELLS;
        int cx = x / texelsPerCell;
        int cy = y / texelsPerCell;
        int cz = z / texelsPerCell;
        double fx = (x % texelsPerCell) / (double) texelsPerCell;
        double fy = (y % texelsPerCell) / (double) texelsPerCell;
        double fz = (z % texelsPerCell) / (double) texelsPerCell;
        double[] corner = new double[8];
        for (int i = 0; i < 8; i++) {
            int dx = i >> 2;
            int dy = (i >> 1) & 1;
            int dz = i & 1;
            int[] g = GRADIENTS[gradient[((c * NOISE_CELLS + (cx + dx) % NOISE_CELLS) * NOISE_CELLS
                + (cy + dy) % NOISE_CELLS) * NOISE_CELLS + (cz + dz) % NOISE_CELLS]];
            corner[i] = g[0] * (fx - dx) + g[1] * (fy - dy) + g[2] * (fz - dz);
        }
        double ux = fade(fx);
        double uy = fade(fy);
        double uz = fade(fz);
        double x00 = Mth.lerp(ux, corner[0], corner[4]);
        double x10 = Mth.lerp(ux, corner[2], corner[6]);
        double x01 = Mth.lerp(ux, corner[1], corner[5]);
        double x11 = Mth.lerp(ux, corner[3], corner[7]);
        return Mth.lerp(uz, Mth.lerp(uy, x00, x10), Mth.lerp(uy, x01, x11));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static int latticeHash(int x, int y, int z, int c) {
        int h = x * 0x27D4EB2D + y * 0x165667B1 + z * 0x9E3779B1 + c * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    private static void resetUnpack(int alignment) {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, alignment);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
    }

    private static void filter(int wrap) {
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, wrap);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, wrap);
    }
}
