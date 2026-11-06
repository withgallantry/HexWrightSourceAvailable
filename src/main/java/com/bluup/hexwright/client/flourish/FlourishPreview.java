package com.bluup.hexwright.client.flourish;

import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.client.ClientTickCounter;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.common.flourish.FlourishFigure;
import com.bluup.hexwright.common.flourish.FlourishMotif;
import com.bluup.hexwright.server.flourish.FlourishSounds;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public final class FlourishPreview {

    private static final double NANOS_PER_TICK = 50_000_000.0;

    private static final int PAUSE_TICKS = 14;

    private static final double EXTENT = 1.2;
    private static final Vec3 SPELL = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 CASTER = new Vec3(2.2, 0.0, 2.4);
    private static final Vec3 EYE = CASTER.add(0.0, 1.62, 0.0);
    private static final Vec3 HAND;
    private static final Vec3 FOCUS = new Vec3(0.8, 1.9, 0.9);
    private static final double SPAN = 7.5;
    private static final int FLOOR_REACH = 4;
    private static final int FLOOR_COLOUR = 0xB4A2D8;

    private static final float BASE_YAW = 20.0f;
    private static final float SWAY_DEGREES = 28.0f;
    private static final float SWAY_SECONDS = 18.0f;
    private static final float MIN_PITCH = 6.0f;
    private static final float MAX_PITCH = 80.0f;

    private static final double ICON_RADIUS = 0.45;
    private static final float ICON_STILL = 0.35f;
    private static final List<float[]> ICON_SIGIL = FlourishLooks.sigilPattern(new Random(7L));

    private static final ResourceLocation CONJURE_SPRITE = new ResourceLocation("hexcasting", "cloud");
    private static final float MOTE_ALPHA = 0.3f;
    private static final double MOTE_FRICTION = 0.96;
    private static final double MOTE_SIZE = 0.135;

    private static final int[] FALLBACK = {0xFF9E61FF, 0xFFD99EFF, 0xFF7A4DEB, 0xFFF2CCFF};

    static {
        Vec3 look = SPELL.subtract(EYE).normalize();
        Vec3 right = look.cross(FlourishPen.UP).normalize();
        HAND = EYE.add(look.scale(0.45)).add(right.scale(0.32)).add(0.0, -0.38, 0.0);
    }

    @Nullable
    private FlourishDesign design;
    @Nullable
    private FlourishLook look;
    private List<DesignedLooks.Mote> motes = List.of();
    private long started;
    private int plays;
    private float yaw = BASE_YAW;
    private float pitch = 26.0f;
    private boolean turnedByHand;
    private final long opened = System.nanoTime();

    public void show(FlourishDesign design) {
        if (!design.equals(this.design)) {
            this.design = design;
            restart();
        }
    }

    public void replay() {
        restart();
        chime(design != null ? design.chime() : null);
    }

    public void turn(double dx, double dy) {
        yaw += (float) dx * 0.8f;
        pitch = Mth.clamp(pitch + (float) dy * 0.6f, MIN_PITCH, MAX_PITCH);
        turnedByHand = true;
    }

    public static void chime(@Nullable Flourish chime) {
        if (chime != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(FlourishSounds.get(chime), 1.0f, 0.9f));
        }
    }

    private void restart() {
        started = System.nanoTime();
        plays++;
        PreviewStage stage = new PreviewStage(plays * 7919);
        look = design != null ? DesignedLooks.create(design, stage) : null;
        motes = design != null ? DesignedLooks.motes(design.motes(), stage, new Random(stage.seed())) : List.of();
    }

    public void draw(GuiGraphics graphics, int x, int y, int width, int height, boolean showCaster, float partialTick) {
        long now = System.nanoTime();
        if (!turnedByHand) {
            double seconds = (now - opened) / 1.0e9;
            yaw = BASE_YAW + SWAY_DEGREES * (float) Math.sin(seconds / SWAY_SECONDS * Mth.TWO_PI);
        }
        float ticks = (float) ((now - started) / NANOS_PER_TICK);
        if (look != null && ticks > look.lifetimeTicks() + PAUSE_TICKS) {
            restart();
            ticks = 0.0f;
        }

        float scale = (float) (Math.min(width, height) / SPAN);
        float pitchRad = pitch * Mth.DEG_TO_RAD;
        float yawRad = yaw * Mth.DEG_TO_RAD;
        Matrix4f view = new Matrix4f(graphics.pose().last().pose())
            .translate(x + width / 2.0f, y + height / 2.0f, 150.0f)
            .scale(scale, -scale, scale)
            .rotateX(pitchRad)
            .rotateY(yawRad);
        Matrix3f toWorld = new Matrix3f().rotateX(pitchRad).rotateY(yawRad).transpose();

        graphics.enableScissor(x, y, x + width, y + height);
        floor(view);
        if (showCaster) {
            caster(graphics, view, scale, pitchRad, yawRad);
        }
        ShaderInstance shader = FlourishShaders.shader();
        Vec3 up = along(toWorld, 0, 1, 0);
        Vec3 right = along(toWorld, 1, 0, 0);
        int[] colours = pigmentColours();
        if (shader != null && look != null && ticks < look.lifetimeTicks()) {
            FlourishPen pen = new FlourishPen(shader, view, FOCUS, up, right, along(toWorld, 0, 0, -1), partialTick);
            begin();
            pen.flourish(ticks / 20.0f, (plays * 37 % 64), 1.0f);
            pen.palette(index -> colours[index]);
            look.draw(pen, ticks / look.lifetimeTicks());
            end();
        }
        if (look != null) {
            begin();
            drawMotes(view, right, up, ticks, look.lifetimeTicks() + PAUSE_TICKS, colours);
            end();
        }
        graphics.disableScissor();
    }

    private void drawMotes(Matrix4f view, Vec3 right, Vec3 up, float ticks, float life, int[] colours) {
        if (motes.isEmpty() || ticks >= life
            || !(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES) instanceof TextureAtlas atlas)) {
            return;
        }
        TextureAtlasSprite sprite = atlas.getSprite(CONJURE_SPRITE);
        float alpha = MOTE_ALPHA * DesignedLooks.MOTE_LAYERS * (1.0f - ticks / life);
        double travelled = (1.0 - Math.pow(MOTE_FRICTION, ticks)) / (1.0 - MOTE_FRICTION);
        double half = MOTE_SIZE * Math.pow(MOTE_FRICTION, ticks);
        Vec3 across = right.scale(half);
        Vec3 upward = up.scale(half);

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        IClientXplatAbstractions.INSTANCE.setFilterSave(atlas, true, false);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (DesignedLooks.Mote mote : motes) {
            Vec3 at = mote.at().add(mote.velocity().scale(travelled)).subtract(FOCUS);
            int colour = colours[mote.colour()];
            moteVertex(builder, view, at.subtract(across).subtract(upward), sprite.getU1(), sprite.getV1(), colour, alpha);
            moteVertex(builder, view, at.subtract(across).add(upward), sprite.getU1(), sprite.getV0(), colour, alpha);
            moteVertex(builder, view, at.add(across).add(upward), sprite.getU0(), sprite.getV0(), colour, alpha);
            moteVertex(builder, view, at.add(across).subtract(upward), sprite.getU0(), sprite.getV1(), colour, alpha);
        }
        BufferUploader.drawWithShader(builder.end());
        IClientXplatAbstractions.INSTANCE.restoreLastFilter(atlas);
    }

    private static void moteVertex(BufferBuilder builder, Matrix4f view, Vec3 at, float u, float v, int colour,
                                   float alpha) {
        builder.vertex(view, (float) at.x, (float) at.y, (float) at.z).uv(u, v)
            .color(colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255))
            .endVertex();
    }

    private static Vec3 along(Matrix3f toWorld, float x, float y, float z) {
        Vector3f v = toWorld.transform(new Vector3f(x, y, z));
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static void floor(Matrix4f view) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = -FLOOR_REACH; i <= FLOOR_REACH; i++) {
            for (int j = -FLOOR_REACH; j < FLOOR_REACH; j++) {
                floorLine(builder, view, j, i, j + 1, i);
                floorLine(builder, view, i, j, i, j + 1);
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void floorLine(BufferBuilder builder, Matrix4f view, double x0, double z0, double x1, double z1) {
        floorVertex(builder, view, x0, z0);
        floorVertex(builder, view, x1, z1);
    }

    private static void floorVertex(BufferBuilder builder, Matrix4f view, double x, double z) {
        double fade = 1.0 - Math.min(Math.sqrt(x * x + z * z) / (FLOOR_REACH + 0.5), 1.0);
        builder.vertex(view, (float) (x - FOCUS.x), (float) -FOCUS.y, (float) (z - FOCUS.z))
            .color(FLOOR_COLOUR >> 16 & 0xFF, FLOOR_COLOUR >> 8 & 0xFF, FLOOR_COLOUR & 0xFF, (int) (fade * fade * 110))
            .endVertex();
    }

    private static void caster(GuiGraphics graphics, Matrix4f view, float scale, float pitchRad, float yawRad) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Vector4f feet = view.transform(new Vector4f((float) (CASTER.x - FOCUS.x), (float) (CASTER.y - FOCUS.y),
            (float) (CASTER.z - FOCUS.z), 1.0f));
        Vec3 toSpell = SPELL.subtract(EYE);
        float bodyYaw = (float) Math.toDegrees(Math.atan2(-toSpell.x, toSpell.z));
        float headPitch = (float) Math.toDegrees(-Math.atan2(toSpell.y, Math.sqrt(toSpell.x * toSpell.x + toSpell.z * toSpell.z)));

        float yBodyRot = player.yBodyRot;
        float yBodyRotO = player.yBodyRotO;
        float yRot = player.getYRot();
        float yRotO = player.yRotO;
        float xRot = player.getXRot();
        float xRotO = player.xRotO;
        float yHeadRot = player.yHeadRot;
        float yHeadRotO = player.yHeadRotO;
        player.yBodyRot = player.yBodyRotO = bodyYaw;
        player.setYRot(bodyYaw);
        player.yRotO = bodyYaw;
        player.setXRot(headPitch);
        player.xRotO = headPitch;
        player.yHeadRot = player.yHeadRotO = bodyYaw;

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        InventoryScreen.renderEntityInInventory(graphics, Math.round(feet.x()), Math.round(feet.y()), Math.round(scale),
            new Quaternionf().rotateX(Mth.PI + pitchRad).rotateY(yawRad), null, player);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        player.yBodyRot = yBodyRot;
        player.yBodyRotO = yBodyRotO;
        player.setYRot(yRot);
        player.yRotO = yRotO;
        player.setXRot(xRot);
        player.xRotO = xRotO;
        player.yHeadRot = yHeadRot;
        player.yHeadRotO = yHeadRotO;
    }

    public static void drawMotif(GuiGraphics graphics, FlourishMotif motif, int x, int y, int size, boolean animate,
                                 boolean dim, int[] colours) {
        if (motif == FlourishMotif.FIGURE) {
            drawFigure(graphics, FlourishFigure.DEFAULT, x, y, size, animate, dim, colours);
            return;
        }
        drawIcon(graphics, x, y, size, motif.ordinal(), DesignedLooks.duration(motif), animate, dim, colours,
            motif.kind() == FlourishMotif.Kind.BAND, (pen, progress) -> icon(pen, motif, progress));
    }

    public static void drawFigure(GuiGraphics graphics, FlourishFigure figure, int x, int y, int size, boolean animate,
                                  boolean dim, int[] colours) {
        drawIcon(graphics, x, y, size, Math.floorMod(figure.hashCode(), 64), figure.lifetimeTicks(), animate, dim,
            colours, false, (pen, progress) -> pen.figure(figure).billboard(FlourishPen.FIGURE, Vec3.ZERO, ICON_RADIUS,
                progress));
    }

    private static void drawIcon(GuiGraphics graphics, int x, int y, int size, int key, int duration, boolean animate,
                                 boolean dim, int[] colours, boolean tilted, Icon icon) {
        ShaderInstance shader = FlourishShaders.shader();
        if (shader == null) {
            return;
        }
        float now = (float) (System.nanoTime() / NANOS_PER_TICK);
        float progress = animate ? ((now + key * 7.0f) % duration) / duration : ICON_STILL;
        float scale = (float) (size / 2.0 / (ICON_RADIUS * 1.15));
        Matrix4f view = new Matrix4f(graphics.pose().last().pose())
            .translate(x + size / 2.0f, y + size / 2.0f, 150.0f)
            .scale(scale, -scale, scale);
        if (tilted) {
            view.rotateX(22.0f * Mth.DEG_TO_RAD);
        }
        FlourishPen pen = new FlourishPen(shader, view, Vec3.ZERO, FlourishPen.UP, new Vec3(1.0, 0.0, 0.0),
            new Vec3(0.0, 0.0, -1.0), 0.0f);

        graphics.enableScissor(x, y, x + size, y + size);
        begin();
        pen.flourish(now / 20.0f, key * 3.1f, dim ? 0.3f : 1.0f);
        pen.palette(index -> colours[index]);
        icon.draw(pen, progress);
        end();
        graphics.disableScissor();
    }

    private interface Icon {
        void draw(FlourishPen pen, float progress);
    }

    private static void icon(FlourishPen pen, FlourishMotif motif, float progress) {
        int style = DesignedLooks.style(motif);
        double r = ICON_RADIUS;
        switch (motif) {
            case SIGIL -> FlourishLooks.sigil(pen, ICON_SIGIL, Vec3.ZERO, r * 0.85, progress);
            case ORBIT -> pen.segment(2.8f, 0.0f, 0.0f, 0.0f).billboard(style, Vec3.ZERO, r, progress);
            case SHARD -> pen.segment(0.6f, 0.0f, 0.0f, 0.0f).billboard(style, Vec3.ZERO, r, progress);
            case THREAD -> pen.axial(style, new Vec3(-r, 0.0, 0.0), new Vec3(r, 0.0, 0.0), r * 0.35, r * 0.5, 0.0, progress);
            case BEAM -> pen.axial(style, new Vec3(0.0, -r, 0.0), new Vec3(0.0, r, 0.0), r * 0.4, r * 0.9, 0.0, progress);
            case STREAK -> pen.segment(0.0f, 0.6f, 0.0f, 0.0f)
                .axial(style, new Vec3(r * 0.8, r * 0.8, 0.0), new Vec3(-r * 0.8, -r * 0.8, 0.0), 0.04, r * 0.5, 0.0, progress);
            case LIGHTNING -> pen.segment(0.3f, 0.0f, 0.0f, 0.0f)
                .axial(style, new Vec3(-r, r * 0.3, 0.0), new Vec3(r, -r * 0.3, 0.0), 0.02, r * 0.6, 0.0, progress);
            case GLYPHS -> pen.band(style, Vec3.ZERO, r * 0.85, r * 0.3, progress);
            case FLAME -> pen.band(style, new Vec3(0.0, -r * 0.1, 0.0), r * 0.8, r * 0.45, progress);
            case CHAINS -> pen.band(style, Vec3.ZERO, r * 0.8, r * 0.4, progress);
            case AURORA -> pen.band(style, Vec3.ZERO, r * 0.8, r * 0.5, progress);
            case BRIAR -> pen.band(style, Vec3.ZERO, r * 0.8, r * 0.5, progress);
            case WILDFLOWER, DANDELION, WORLD_TREE -> FlourishLooks.plant(pen, style, new Vec3(0.0, -r, 0.0), r * 2.0, progress);
            case BLADE -> pen.segment(0.0f, 0.25f, 0.0f, 0.0f)
                .axial(style, new Vec3(0.0, r * 2.0, 0.0), new Vec3(0.0, -r * 0.75, 0.0), r * 1.7, r * 0.4, r * 0.4, progress);
            default -> pen.billboard(style, Vec3.ZERO, r, progress);
        }
    }

    public static int[] pigmentColours() {
        LocalPlayer player = Minecraft.getInstance().player;
        ColorProvider provider = null;
        if (player != null) {
            try {
                provider = ClientFlourishes.provider(IXplatAbstractions.INSTANCE.getPigment(player));
            } catch (RuntimeException ignored) {
            }
        }
        if (provider == null) {
            return FALLBACK.clone();
        }
        float time = ClientTickCounter.getTotal();
        int[] colours = new int[4];
        for (int i = 0; i < colours.length; i++) {
            colours[i] = ClientFlourishes.colour(provider, i, time);
        }
        return colours;
    }

    private static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    private static void end() {
        ShaderInstance reset = GameRenderer.getPositionColorShader();
        if (reset != null) {
            reset.apply();
            reset.clear();
        }
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private record PreviewStage(int seed) implements DesignedLooks.Stage {
        @Override
        public Vec3 centre() {
            return SPELL;
        }

        @Override
        public double extent() {
            return EXTENT;
        }

        @Override
        public Vec3 facing() {
            return EYE.subtract(SPELL).normalize();
        }

        @Override
        public Vec3 floor() {
            return new Vec3(SPELL.x, 0.04, SPELL.z);
        }

        @Override
        public Vec3 standing(double radius) {
            return SPELL.y - 0.04 < radius ? new Vec3(SPELL.x, 0.04 + radius, SPELL.z) : SPELL;
        }

        @Override
        public Vec3 hand(float partialTick) {
            return HAND;
        }
    }
}
