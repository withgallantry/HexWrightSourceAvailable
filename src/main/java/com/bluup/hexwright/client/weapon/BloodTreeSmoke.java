package com.bluup.hexwright.client.weapon;

import at.petrak.hexcasting.client.ClientTickCounter;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.server.weapon.BloodTreeEntity;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class BloodTreeSmoke {

    private static final int PLUMES = 14;
    private static final float START_CHANCE = 0.2F;
    private static final double SEEN_FROM = 64.0D;

    private static final int PUFF_LIFE = 40;
    private static final float FADE_IN_TICKS = 4.0F;
    private static final float HEAD_WIDTH = 0.1F;
    private static final float TAIL_WIDTH = 0.6F;
    private static final float SPACING = 0.08F;

    private static final double DRAG = 0.96D;
    private static final double LIFT = 0.004D;
    private static final float TIGHTENING = 2.0F;
    private static final float SWAY = 0.5F;
    private static final float SWAY_RATE = 0.08F;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<BloodTreeEntity, List<Plume>> TREES = new IdentityHashMap<>();

    private BloodTreeSmoke() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BloodTreeSmoke::tick);
        WorldRenderEvents.LAST.register(BloodTreeSmoke::render);
    }

    private static void tick(Minecraft client) {
        Entity camera = client.getCameraEntity();
        if (client.level == null || camera == null) {
            TREES.clear();
            return;
        }
        if (client.isPaused()) {
            return;
        }
        List<BloodTreeEntity> trees = client.level.getEntitiesOfClass(BloodTreeEntity.class,
            new AABB(camera.position(), camera.position()).inflate(SEEN_FROM));
        TREES.keySet().retainAll(trees);
        for (BloodTreeEntity tree : trees) {
            List<Plume> plumes = TREES.computeIfAbsent(tree, key -> new ArrayList<>());
            plumes.removeIf(plume -> !plume.tick());
            if (tree.presence() >= 1.0F && plumes.size() < PLUMES && RANDOM.nextFloat() < START_CHANCE) {
                plumes.add(new Plume(tree.somewhereOnIt(RANDOM), tree.position()));
            }
        }
    }

    private static void render(WorldRenderContext context) {
        ShaderInstance shader = BloodSmokeShaders.shader();
        if (shader == null || TREES.isEmpty() || RemoteLevelManager.isRemotePassActive()) {
            return;
        }
        Matrix4f pose = context.matrixStack().last().pose();
        Vec3 camera = context.camera().getPosition();
        float partialTick = context.tickDelta();

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        int quads = 0;
        for (Map.Entry<BloodTreeEntity, List<Plume>> tree : TREES.entrySet()) {
            if (tree.getKey().level() != context.world()) {
                continue;
            }
            float strength = tree.getKey().presence();
            for (Plume plume : tree.getValue()) {
                quads += plume.draw(builder, pose, camera, partialTick, strength);
            }
        }
        if (quads == 0) {
            builder.end().release();
            return;
        }

        shader.safeGetUniform("Time").set((ClientTickCounter.getTotal() % 12000.0F) / 20.0F);
        BlendMode previousBlend = BlendModeAccessor.hexwright$getLastApplied();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        BlendModeAccessor.hexwright$setLastApplied(previousBlend);
    }

    private static final class Plume {

        private final Vec3 source;
        private final Vec3 heading;
        private final Vector3d axis;
        private final float spin;
        private final float swayPhase;
        private final double speed;
        private final int emitTicks;
        private final float seed;
        private final ArrayDeque<Puff> puffs = new ArrayDeque<>();
        private int age;

        private Plume(Vec3 source, Vec3 trunk) {
            this.source = source;
            Vec3 out = new Vec3(source.x - trunk.x, 0.0D, source.z - trunk.z).normalize();
            this.heading = out.scale(0.6D).add(0.0D, 1.0D, 0.0D).normalize();
            this.axis = new Vector3d(this.heading.x, this.heading.y, this.heading.z)
                .cross(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
            if (this.axis.lengthSquared() < 1.0E-8D) {
                this.axis.set(1.0D, 0.0D, 0.0D);
            }
            this.axis.normalize();
            this.spin = (0.03F + RANDOM.nextFloat() * 0.04F) * (RANDOM.nextBoolean() ? 1.0F : -1.0F);
            this.swayPhase = RANDOM.nextFloat() * Mth.TWO_PI;
            this.speed = 0.05D + RANDOM.nextDouble() * 0.03D;
            this.emitTicks = 40 + RANDOM.nextInt(50);
            this.seed = RANDOM.nextFloat() * 100.0F;
        }

        private boolean tick() {
            this.age++;
            for (Puff puff : this.puffs) {
                puff.tick(this);
            }
            while (!this.puffs.isEmpty() && this.puffs.peekLast().age >= PUFF_LIFE) {
                this.puffs.removeLast();
            }
            if (this.age <= this.emitTicks) {
                Vec3 heading = this.heading.yRot(SWAY * Mth.sin(this.age * SWAY_RATE + this.swayPhase))
                    .scale(this.speed);
                this.puffs.addFirst(new Puff(this.source, heading, this.age));
            }
            return !this.puffs.isEmpty();
        }

        private int draw(BufferBuilder builder, Matrix4f pose, Vec3 camera, float partialTick, float strength) {
            int count = this.puffs.size();
            if (count < 2 || strength <= 0.0F) {
                return 0;
            }
            Vec3[] at = new Vec3[count];
            Puff[] puffs = this.puffs.toArray(new Puff[0]);
            for (int n = 0; n < count; n++) {
                at[n] = puffs[n].at(partialTick).subtract(camera);
            }
            Vec3[] sides = new Vec3[count];
            Vec3 side = null;
            for (int n = 0; n < count; n++) {
                Vec3 across = at[Math.max(n - 1, 0)].subtract(at[Math.min(n + 1, count - 1)]).cross(at[n]);
                side = across.lengthSqr() > 1.0E-10D ? across.normalize() : side;
                sides[n] = side;
            }
            if (side == null) {
                return 0;
            }
            for (int n = count - 2; n >= 0; n--) {
                if (sides[n] == null) {
                    sides[n] = sides[n + 1];
                }
            }
            for (int n = 0; n + 1 < count; n++) {
                vertex(builder, pose, at[n], sides[n], puffs[n], -1.0F, partialTick, strength);
                vertex(builder, pose, at[n], sides[n], puffs[n], 1.0F, partialTick, strength);
                vertex(builder, pose, at[n + 1], sides[n + 1], puffs[n + 1], 1.0F, partialTick, strength);
                vertex(builder, pose, at[n + 1], sides[n + 1], puffs[n + 1], -1.0F, partialTick, strength);
            }
            return count - 1;
        }

        private void vertex(BufferBuilder builder, Matrix4f pose, Vec3 at, Vec3 side, Puff puff, float across,
                            float partialTick, float strength) {
            float age = puff.age + partialTick;
            float life = Math.min(1.0F, age / PUFF_LIFE);
            float halfWidth = Mth.lerp(life, HEAD_WIDTH, TAIL_WIDTH);
            float left = Math.min(1.0F, age / FADE_IN_TICKS) * (1.0F - life) * strength;
            Vec3 corner = at.add(side.scale(halfWidth * across));
            builder.vertex(pose, (float) corner.x, (float) corner.y, (float) corner.z)
                .color(halfWidth, 0.0F, 0.0F, left)
                .uv(across, this.seed + puff.born * SPACING)
                .endVertex();
        }
    }

    private static final class Puff {

        private final Vector3d velocity;
        private final int born;
        private double x;
        private double y;
        private double z;
        private double xo;
        private double yo;
        private double zo;
        private int age;

        private Puff(Vec3 from, Vec3 velocity, int born) {
            this.x = this.xo = from.x;
            this.y = this.yo = from.y;
            this.z = this.zo = from.z;
            this.velocity = new Vector3d(velocity.x, velocity.y, velocity.z);
            this.born = born;
        }

        private void tick(Plume plume) {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            this.age++;
            this.velocity.rotateAxis(plume.spin * (1.0F + TIGHTENING * this.age / PUFF_LIFE),
                plume.axis.x, plume.axis.y, plume.axis.z).mul(DRAG);
            this.velocity.y += LIFT;
            this.x += this.velocity.x;
            this.y += this.velocity.y;
            this.z += this.velocity.z;
        }

        private Vec3 at(float partialTick) {
            return new Vec3(Mth.lerp(partialTick, this.xo, this.x), Mth.lerp(partialTick, this.yo, this.y),
                Mth.lerp(partialTick, this.zo, this.z));
        }
    }
}
