package com.bluup.hexwright.client.hexahedron;

import at.petrak.hexcasting.client.ClientTickCounter;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.server.hexahedron.Hexahedron;
import com.bluup.hexwright.server.hexahedron.RiftAnchor;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Camera;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HexahedronRenderer {

    private static final double SEEN_FROM = 2400.0;
    private static final double FADES_FROM = 1800.0;
    private static final int SLICES = 6;
    private static final int FAST_SLICES = 3;
    private static final float SLICE_SPACING = 3.5f;
    private static final float FAR_SHARE = 0.45f;
    private static final int STORM_TEXELS = 384;
    private static final float TIME_WRAP_TICKS = 24000.0f;
    private static final float OVERCAST_HEIGHT = 40.0f;
    private static final float OVERCAST_RADIUS = 1600.0f;
    private static final int MOTES = 640;
    private static final float MOTE_LIFE = 150.0f;
    private static final float MOTE_DRAG = 0.04f;
    private static final float BURST_SWEEP = 0.7f;

    public static final ResourceLocation CUBE_PHASE = Hexwright.id("hexahedron_cube");

    @Nullable
    private static TextureTarget storm;

    private HexahedronRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_SETUP.register(context -> render(context, false));
        WorldRenderEvents.AFTER_SETUP.register(CUBE_PHASE, context -> render(context, true));
        WorldRenderEvents.AFTER_SETUP.addPhaseOrdering(Event.DEFAULT_PHASE, CUBE_PHASE);
    }

    private static void render(WorldRenderContext context, boolean cubes) {
        ClientLevel level = context.world();
        float partialTick = context.tickDelta();
        List<ClientHexahedra.Shown> shown = ClientHexahedra.shown(level, partialTick);
        if (shown.isEmpty() || !HexahedronShaders.ready() || IrisCompat.isRenderingShadowPass()
            || PortalViewRenderer.isRenderingView() || RemoteLevelManager.isRemotePassActive()
            || skyHidden(context.camera())) {
            return;
        }
        Vec3 eye = context.camera().getPosition();
        Minecraft minecraft = Minecraft.getInstance();
        Frame frame = new Frame(
            level.getGameTime() + partialTick,
            (ClientTickCounter.getTotal() % TIME_WRAP_TICKS) / 20.0f,
            level.getSkyDarken(partialTick),
            minecraft.options.graphicsMode().get() == GraphicsStatus.FAST,
            minecraft.gameRenderer.getDepthFar());

        BlendMode previousBlend = BlendModeAccessor.hexwright$getLastApplied();
        BlendModeAccessor.hexwright$setLastApplied(null);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(context.matrixStack().last().pose());
        RenderSystem.applyModelViewMatrix();

        List<ClientHexahedra.Shown> farthestFirst = new ArrayList<>(shown);
        farthestFirst.sort(Comparator.comparingDouble(
            (ClientHexahedra.Shown each) -> each.hexahedron().anchor().distanceToSqr(eye)).reversed());
        for (ClientHexahedra.Shown each : farthestFirst) {
            draw(each, eye, frame, cubes);
        }

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        BlendModeAccessor.hexwright$setLastApplied(previousBlend);
    }

    private record Frame(double gameTime, float time, float daylight, boolean fast, float far) {
    }

    private static void draw(ClientHexahedra.Shown shown, Vec3 eye, Frame frame, boolean cubes) {
        Hexahedron hexahedron = shown.hexahedron();
        float emergence = shown.emergence();
        Vec3 anchor = hexahedron.anchor();
        double across = Math.hypot(eye.x - anchor.x, eye.z - anchor.z);
        if (emergence <= 0.001f || across >= SEEN_FROM) {
            return;
        }
        Vector3f camera = new Vector3f((float) (eye.x - anchor.x), (float) (eye.y - anchor.y), (float) (eye.z - anchor.z));
        boolean below = camera.y < 0.0f;
        if (cubes && !below) {
            return;
        }
        float fade = (float) Mth.clamp((SEEN_FROM - across) / (SEEN_FROM - FADES_FROM), 0.0, 1.0);
        Matrix3f basis = hexahedron.basis(frame.gameTime());
        Vector3f centre = Hexahedron.centre(shown.through());
        float outer = hexahedron.stormRadius();
        int slices = frame.fast() ? FAST_SLICES : SLICES;

        List<Vector3f> triangles = cubeTriangles(centre, basis, camera);
        List<Vector2f> hole = crossSection(centre, basis);
        float reach = 0.0f;
        for (Vector3f vertex : triangles) {
            reach = Math.max(reach, vertex.distance(camera));
        }
        for (float y : new float[]{0.0f, (slices - 1) * SLICE_SPACING}) {
            for (int corner = 0; corner < 4; corner++) {
                reach = Math.max(reach, camera.distance(corner < 2 ? -outer : outer, y, corner % 2 == 0 ? -outer : outer));
            }
        }
        float scale = Math.max(1.0f, reach / (FAR_SHARE * frame.far()));
        long burstAt = hexahedron.burstAt();
        double sinceBurst = burstAt == RiftAnchor.NEVER ? -1.0 : frame.gameTime() - burstAt;
        float burst = hexahedron.burst(frame.gameTime());
        float flash = sinceBurst < 0.0 ? 0.0f : (float) Math.exp(-sinceBurst / 8.0);

        if (!cubes) {
            bakeStorm(outer, hole, frame);
        }

        ShaderInstance cube = HexahedronShaders.cube();
        ShaderInstance clouds = HexahedronShaders.clouds();
        for (ShaderInstance shader : new ShaderInstance[]{cube, clouds}) {
            shader.safeGetUniform("Scale").set(scale);
            shader.safeGetUniform("CameraOffset").set(camera);
            shader.safeGetUniform("Time").set(frame.time());
            shader.safeGetUniform("Pulse").set(Math.max(shown.flare(), flash));
            shader.safeGetUniform("Emerge").set(emergence);
            shader.safeGetUniform("Daylight").set(frame.daylight());
            shader.safeGetUniform("Fade").set(fade);
        }
        cube.safeGetUniform("CubeCenter").set(centre);
        cube.safeGetUniform("CubeBasis").set(basis);
        cube.safeGetUniform("HalfSize").set(Hexahedron.HALF_SIZE);
        cube.safeGetUniform("Dissolve").set(burst);
        cube.setSampler("Sigils", HexahedronSigils.atlas());
        float clearing = 1.0f - hexahedron.cleared(frame.gameTime());
        clouds.safeGetUniform("Fade").set(fade * clearing * clearing);
        clouds.safeGetUniform("Outer").set(outer);
        clouds.setSampler("Storm", storm);

        if (!cubes) {
            drawOvercast(camera, frame, fade * emergence * clearing);
        }
        if (cubes || !below) {
            if (burst < 1.0f) {
                drawCube(cube, triangles, camera, scale);
            }
            drawMotes(hexahedron, centre, basis, camera, scale, sinceBurst);
        }
        if (cubes) {
            return;
        }
        for (int i = 0; i < slices; i++) {
            int slice = below ? slices - 1 - i : i;
            clouds.safeGetUniform("Layer").set(slice / (float) (slices - 1));
            drawSlice(clouds, slice * SLICE_SPACING, outer, camera, scale);
        }
    }

    private static void bakeStorm(float outer, List<Vector2f> hole, Frame frame) {
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        PortalViewRenderer.suspendScissor();
        try {
            if (storm == null) {
                storm = new TextureTarget(STORM_TEXELS, STORM_TEXELS, false, Minecraft.ON_OSX);
            }
            storm.bindWrite(true);
            ShaderInstance shader = HexahedronShaders.storm();
            float radius = 0.0f;
            for (Vector2f corner : hole) {
                radius = Math.max(radius, corner.length());
            }
            shader.safeGetUniform("Outer").set(outer);
            shader.safeGetUniform("HoleRadius").set(radius);
            shader.safeGetUniform("Time").set(frame.time());
            shader.safeGetUniform("Fast").set(frame.fast() ? 1.0f : 0.0f);
            String[] pairs = {"HoleA", "HoleB", "HoleC"};
            for (int pair = 0; pair < pairs.length; pair++) {
                Vector2f a = corner(hole, pair * 2);
                Vector2f b = corner(hole, pair * 2 + 1);
                shader.safeGetUniform(pairs[pair]).set(a.x, a.y, b.x, b.y);
            }
            RenderSystem.setShader(() -> shader);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.vertex(-1.0, -1.0, 0.0).endVertex();
            builder.vertex(1.0, -1.0, 0.0).endVertex();
            builder.vertex(1.0, 1.0, 0.0).endVertex();
            builder.vertex(-1.0, 1.0, 0.0).endVertex();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            PortalViewRenderer.resumeScissor();
        }
    }

    private static Vector2f corner(List<Vector2f> hole, int index) {
        return hole.isEmpty() ? new Vector2f() : hole.get(Math.min(index, hole.size() - 1));
    }

    private static void drawCube(ShaderInstance shader, List<Vector3f> triangles, Vector3f camera, float scale) {
        if (triangles.isEmpty()) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        for (Vector3f vertex : triangles) {
            vertex(builder, vertex.x, vertex.y, vertex.z, camera, scale);
        }
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void drawOvercast(Vector3f camera, Frame frame, float fade) {
        if (fade <= 0.0f) {
            return;
        }
        float reach = 0.0f;
        for (int corner = 0; corner < 4; corner++) {
            reach = Math.max(reach, camera.distance(corner < 2 ? -OVERCAST_RADIUS : OVERCAST_RADIUS, OVERCAST_HEIGHT,
                corner % 2 == 0 ? -OVERCAST_RADIUS : OVERCAST_RADIUS));
        }
        float scale = Math.max(1.0f, reach / (FAR_SHARE * frame.far()));
        ShaderInstance shader = HexahedronShaders.overcast();
        shader.safeGetUniform("Scale").set(scale);
        shader.safeGetUniform("CameraOffset").set(camera);
        shader.safeGetUniform("Time").set(frame.time());
        shader.safeGetUniform("Daylight").set(frame.daylight());
        shader.safeGetUniform("Fade").set(fade);
        shader.safeGetUniform("Outer").set(OVERCAST_RADIUS);
        drawSlice(shader, OVERCAST_HEIGHT, OVERCAST_RADIUS, camera, scale);
    }

    private static void drawMotes(Hexahedron hexahedron, Vector3f centre, Matrix3f basis, Vector3f camera, float scale,
                                  double since) {
        if (since < 0.0 || since > Hexahedron.BURST_TICKS + MOTE_LIFE) {
            return;
        }
        long seed = hexahedron.id().getLeastSignificantBits();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < MOTES; i++) {
            int face = Math.min((int) (random(seed, i, 0) * 6.0f), 5);
            Vector3f local = new Vector3f();
            local.setComponent(face % 3, face < 3 ? -1.0f : 1.0f);
            local.setComponent((face + 1) % 3, random(seed, i, 1) * 2.0f - 1.0f);
            local.setComponent((face + 2) % 3, random(seed, i, 2) * 2.0f - 1.0f);
            Vector3f start = onCube(local, centre, basis);
            if (start.y > 0.0f) {
                continue;
            }
            float low = Mth.clamp(0.5f - (start.y - centre.y) / (2.0f * Hexahedron.HALF_DIAGONAL), 0.0f, 1.0f);
            float release = Hexahedron.BURST_TICKS
                * Mth.clamp(((1.0f - low) * BURST_SWEEP + 0.2f + 0.6f * random(seed, i, 3)) / (1.0f + BURST_SWEEP), 0.0f, 1.0f);
            float life = MOTE_LIFE * (0.5f + 0.5f * random(seed, i, 4));
            float age = (float) since - release;
            if (age < 0.0f || age > life) {
                continue;
            }
            Vector3f out = new Vector3f(start).sub(centre).normalize()
                .add((random(seed, i, 5) - 0.5f) * 0.8f, 0.25f + 0.4f * random(seed, i, 6), (random(seed, i, 7) - 0.5f) * 0.8f)
                .normalize();
            float speed = 1.2f + 2.8f * random(seed, i, 8);
            float travel = speed * (1.0f - (float) Math.exp(-MOTE_DRAG * age)) / MOTE_DRAG;
            Vector3f at = out.mul(travel).add(start).add(0.0f, 0.06f * age, 0.0f);
            float left = 1.0f - age / life;
            mote(builder, at, (2.5f + 3.5f * random(seed, i, 9)) * (0.5f + 0.5f * left), random(seed, i, 10), left * left,
                camera, scale);
        }
        ShaderInstance shader = HexahedronShaders.mote();
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void mote(BufferBuilder builder, Vector3f at, float size, float heat, float alpha, Vector3f camera,
                             float scale) {
        Vector3f toEye = new Vector3f(camera).sub(at).normalize();
        Vector3f right = toEye.cross(0.0f, 1.0f, 0.0f, new Vector3f());
        if (right.lengthSquared() < 1.0e-6f) {
            right.set(1.0f, 0.0f, 0.0f);
        }
        right.normalize(size);
        Vector3f up = right.cross(toEye, new Vector3f()).normalize(size);
        for (float[] corner : new float[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
            float x = at.x + right.x * corner[0] + up.x * corner[1];
            float y = at.y + right.y * corner[0] + up.y * corner[1];
            float z = at.z + right.z * corner[0] + up.z * corner[1];
            builder.vertex((x - camera.x) / scale, (y - camera.y) / scale, (z - camera.z) / scale)
                .uv(corner[0], corner[1]).color(heat, 0.0f, 0.0f, alpha).endVertex();
        }
    }

    private static float random(long seed, int index, int what) {
        long h = seed ^ index * 0x9E3779B97F4A7C15L ^ what * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return ((h ^ (h >>> 31)) >>> 40) / (float) (1L << 24);
    }

    private static void drawSlice(ShaderInstance shader, float y, float outer, Vector3f camera, float scale) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        vertex(builder, -outer, y, -outer, camera, scale);
        vertex(builder, outer, y, -outer, camera, scale);
        vertex(builder, outer, y, outer, camera, scale);
        vertex(builder, -outer, y, outer, camera, scale);
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z, Vector3f camera, float scale) {
        builder.vertex((x - camera.x) / scale, (y - camera.y) / scale, (z - camera.z) / scale).endVertex();
    }

    private static List<Vector3f> cubeTriangles(Vector3f centre, Matrix3f basis, Vector3f camera) {
        List<Vector3f> triangles = new ArrayList<>();
        for (int axis = 0; axis < 3; axis++) {
            for (float sign = -1.0f; sign <= 1.0f; sign += 2.0f) {
                Vector3f normal = basis.getColumn(axis, new Vector3f()).mul(sign);
                Vector3f middle = normal.mul(Hexahedron.HALF_SIZE, new Vector3f()).add(centre);
                if (normal.dot(camera.sub(middle, new Vector3f())) <= 0.0f) {
                    continue;
                }
                int u = (axis + 1) % 3;
                int v = (axis + 2) % 3;
                List<Vector3f> corners = new ArrayList<>(4);
                for (float[] at : new float[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
                    Vector3f local = new Vector3f();
                    local.setComponent(axis, sign);
                    local.setComponent(u, at[0]);
                    local.setComponent(v, at[1]);
                    corners.add(onCube(local, centre, basis));
                }
                List<Vector3f> polygon = belowRift(corners);
                for (int i = 1; i + 1 < polygon.size(); i++) {
                    triangles.add(polygon.get(0));
                    triangles.add(polygon.get(i));
                    triangles.add(polygon.get(i + 1));
                }
            }
        }
        return triangles;
    }

    private static List<Vector2f> crossSection(Vector3f centre, Matrix3f basis) {
        List<Vector2f> corners = new ArrayList<>(6);
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;
            for (float cu = -1.0f; cu <= 1.0f; cu += 2.0f) {
                for (float cv = -1.0f; cv <= 1.0f; cv += 2.0f) {
                    Vector3f a = new Vector3f();
                    a.setComponent(axis, -1.0f);
                    a.setComponent(u, cu);
                    a.setComponent(v, cv);
                    Vector3f b = new Vector3f(a).setComponent(axis, 1.0f);
                    Vector3f pa = onCube(a, centre, basis);
                    Vector3f pb = onCube(b, centre, basis);
                    if ((pa.y < 0.0f) != (pb.y < 0.0f)) {
                        Vector3f crossing = pa.lerp(pb, pa.y / (pa.y - pb.y), new Vector3f());
                        corners.add(new Vector2f(crossing.x, crossing.z));
                    }
                }
            }
        }
        Vector2f middle = new Vector2f();
        corners.forEach(middle::add);
        middle.div(Math.max(corners.size(), 1));
        corners.sort(Comparator.comparingDouble(c -> Math.atan2(c.y - middle.y, c.x - middle.x)));
        return corners;
    }

    private static Vector3f onCube(Vector3f local, Vector3f centre, Matrix3f basis) {
        return basis.transform(local.mul(Hexahedron.HALF_SIZE, new Vector3f())).add(centre);
    }

    private static List<Vector3f> belowRift(List<Vector3f> polygon) {
        List<Vector3f> out = new ArrayList<>(polygon.size() + 1);
        for (int i = 0; i < polygon.size(); i++) {
            Vector3f p = polygon.get(i);
            Vector3f q = polygon.get((i + 1) % polygon.size());
            if (p.y <= 0.0f) {
                out.add(p);
            }
            if ((p.y <= 0.0f) != (q.y <= 0.0f)) {
                out.add(p.lerp(q, p.y / (p.y - q.y), new Vector3f()));
            }
        }
        return out;
    }

    public static boolean skyHidden(Camera camera) {
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.LAVA || fluid == FogType.POWDER_SNOW) {
            return true;
        }
        return camera.getEntity() instanceof LivingEntity living
            && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }
}
