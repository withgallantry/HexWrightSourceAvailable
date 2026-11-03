package com.bluup.hexwright.client.soulforge;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.client.render.emissive.EmissiveBakedModel;
import com.bluup.hexwright.client.render.emissive.EmissiveBloom;
import com.bluup.hexwright.client.render.emissive.EmissiveGlowLayer;
import com.bluup.hexwright.server.soulforge.SoulforgedItem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SoulforgedMaterialisation {

    private static final float DURATION_TICKS = 16.0F;

    private static final int SYNC_GRACE_TICKS = 4;

    private static final float FORMED_AT = 0.85F;

    private static final float ENERGY_REACH = 0.35F;

    private static final float AFTERGLOW_REACH = 0.35F;
    private static final float AFTERGLOW_ALPHA = 0.6F;

    private static final float ENERGY_TAIL_ALPHA = 0.45F;

    private static final float HALO_STRENGTH = 0.3F;

    private static final float EPSILON = 1.0e-4F;

    private static final long QUAD_SEED = 42L;

    private static final Direction[] DIRECTIONS = Direction.values();

    private static final int STRIDE = DefaultVertexFormat.BLOCK.getIntegerSize();
    private static final int U_OFFSET = 4;

    private static final Map<Item, ItemStack> GHOSTS = new HashMap<>();

    private static final Map<Long, Materialisation> ACTIVE = new HashMap<>();

    private static long clientTicks;

    private SoulforgedMaterialisation() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTicks++;
            if (client.level == null) {
                ACTIVE.clear();
                return;
            }
            ACTIVE.values().removeIf(state -> state.age(0.0F) > DURATION_TICKS + 1.0F);
        });
    }

    public static void handle(int entityId, Item item, InteractionHand hand, boolean arriving) {
        ACTIVE.put(key(entityId, hand), new Materialisation(clientTicks, arriving, item));
        HexwrightDebug.log(HexwrightDebug.RENDER, "[soulforged] {} {} {} of entity {}",
            item, arriving ? "materialising in" : "dissolving out of", hand, entityId);
    }

    @Nullable
    public static ItemStack shownIn(LivingEntity holder, InteractionHand hand, ItemStack actual) {
        Materialisation state = ACTIVE.get(key(holder.getId(), hand));
        if (state == null || state.age(0.0F) >= DURATION_TICKS) {
            return null;
        }
        if (actual.is(state.item())) {
            return actual;
        }
        if (actual.isEmpty() && (!state.arriving() || clientTicks - state.start() < SYNC_GRACE_TICKS)) {
            return ghost(state.item());
        }
        return null;
    }

    public static boolean render(LivingEntity holder, ItemStack stack, ItemDisplayContext context,
                                 boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (!(stack.getItem() instanceof SoulforgedItem item)) {
            return false;
        }
        Materialisation state = inHand(context) ? ACTIVE.get(key(holder.getId(), handOf(holder, leftHand))) : null;
        float age = state != null && stack.is(state.item())
            ? state.age(Minecraft.getInstance().getFrameTime())
            : DURATION_TICKS;
        if (age >= DURATION_TICKS) {
            return GHOSTS.get(stack.getItem()) == stack;
        }
        float elapsed = Math.max(0.0F, age) / DURATION_TICKS;
        draw(holder, stack, item.materialiseColour(), context, leftHand, poseStack, buffers, light,
            state.arriving() ? elapsed : 1.0F - elapsed);
        return true;
    }

    private static ItemStack ghost(Item item) {
        return GHOSTS.computeIfAbsent(item, ItemStack::new);
    }

    private static void draw(LivingEntity holder, ItemStack stack, int colour, ItemDisplayContext context,
                             boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, int light,
                             float progress) {
        BakedModel model = Minecraft.getInstance().getItemRenderer()
            .getModel(stack, holder.level(), holder, holder.getId() + context.ordinal());
        List<Face> faces = facesOf(model);
        if (faces.isEmpty()) {
            return;
        }

        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (Face face : shapeOf(faces)) {
            for (Corner corner : face.corners()) {
                low = Math.min(low, corner.y());
                high = Math.max(high, corner.y());
            }
        }
        ItemTransform transform = model.getTransforms().getTransform(context);
        float grip = Mth.clamp(gripHeight(transform, leftHand), low, high);
        float length = Math.max(high - grip, grip - low);

        Stage stage = Stage.at(progress, grip, length);

        poseStack.pushPose();
        transform.apply(leftHand, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        PoseStack.Pose pose = poseStack.last();

        RenderType itemLayer = Sheets.translucentCullBlockSheet();
        emitRange(buffers.getBuffer(itemLayer), pose, faces, grip, 0.0F, stage.drawn(), distance -> 0xFFFFFFFF, light);

        if (!IrisCompat.isRenderingShadowPass()) {
            List<Face> parts = glowingPartsOf(model);
            int partsTint = grey(model instanceof EmissiveBakedModel emissive ? emissive.params().strength() : 1.0F);

            RenderType partsLayer = EmissiveGlowLayer.maskedLayer();
            RenderType energyLayer = Objects.requireNonNullElse(EmissiveGlowLayer.silhouetteLayer(), partsLayer);
            MultiBufferSource.BufferSource source =
                buffers instanceof MultiBufferSource.BufferSource batched ? batched : null;
            if (source != null) {
                source.endBatch(itemLayer);
            }
            emitParts(buffers.getBuffer(partsLayer), pose, parts, partsTint, stage);
            if (source != null) {
                source.endBatch(partsLayer);
            }
            emitEnergy(buffers.getBuffer(energyLayer), pose, faces, stage, colour, 1.0F);
            if (source != null) {
                source.endBatch(energyLayer);
            }
            EmissiveBloom.capture(partsLayer, consumer -> emitParts(consumer, pose, parts, partsTint, stage));
            EmissiveBloom.capture(energyLayer, consumer ->
                emitEnergy(consumer, pose, faces, stage, colour, HALO_STRENGTH));
        }
        poseStack.popPose();
    }

    private static void emitParts(VertexConsumer consumer, PoseStack.Pose pose, List<Face> parts, int partsTint,
                                  Stage stage) {
        emitRange(consumer, pose, parts, stage.grip(), 0.0F, stage.drawn(), distance -> partsTint,
            LightTexture.FULL_BRIGHT);
    }

    private static void emitEnergy(VertexConsumer consumer, PoseStack.Pose pose, List<Face> faces, Stage stage,
                                   int colour, float strength) {
        int settled = scaled(colour, strength);
        int burning = scaled(brightened(colour), strength);
        emitRange(consumer, pose, faces, stage.grip(), stage.solid(), stage.energy(), distance -> {
            float lead = phase(distance, stage.solid(), stage.energy());
            return withAlpha(Mth.lerp(lead, ENERGY_TAIL_ALPHA, 1.0F), FastColor.ARGB32.lerp(lead, settled, burning));
        }, LightTexture.FULL_BRIGHT);
        emitRange(consumer, pose, faces, stage.grip(), stage.afterglowFrom(), stage.solid(), distance ->
                withAlpha(stage.afterglowAlpha() * phase(distance, stage.afterglowFrom(), stage.solid()), settled),
            LightTexture.FULL_BRIGHT);
    }

    private static List<Face> shapeOf(List<Face> faces) {
        List<Face> shape = faces.stream().filter(face -> !face.animated()).toList();
        return shape.isEmpty() ? faces : shape;
    }

    private static List<Face> glowingPartsOf(BakedModel model) {
        if (model instanceof EmissiveBakedModel emissive && emissive.hasTwin() && !emissive.thresholded()) {
            BakedModel twin = emissive.glowModel();
            if (twin != null) {
                return facesOf(twin);
            }
        }
        return List.of();
    }

    private static float gripHeight(ItemTransform transform, boolean leftHand) {
        PoseStack local = new PoseStack();
        transform.apply(leftHand, local);
        local.translate(-0.5F, -0.5F, -0.5F);
        float height = local.last().pose().invert(new Matrix4f()).transformPosition(new Vector3f()).y();
        return Float.isFinite(height) ? height : 0.5F;
    }

    private static void emitRange(VertexConsumer consumer, PoseStack.Pose pose, List<Face> faces, float grip,
                                  float from, float to, Tint tint, int light) {
        if (to - from <= EPSILON) {
            return;
        }
        for (Face face : faces) {
            emitClipped(consumer, pose, face, grip + from, grip + to, grip, tint, light);
            emitClipped(consumer, pose, face, grip - to, grip - from, grip, tint, light);
        }
    }

    private static void emitClipped(VertexConsumer consumer, PoseStack.Pose pose, Face face, float bottom,
                                    float top, float grip, Tint tint, int light) {
        List<Corner> polygon = clip(clip(face.corners(), bottom, true), top, false);
        if (polygon.size() < 3) {
            return;
        }
        Vector3f normal = pose.normal().transform(new Vector3f(face.normalX(), face.normalY(), face.normalZ()));
        int last = polygon.size() - 1;
        for (int i = 1; i < last; i += 2) {
            vertex(consumer, pose, polygon.get(0), normal, grip, tint, light);
            vertex(consumer, pose, polygon.get(i), normal, grip, tint, light);
            vertex(consumer, pose, polygon.get(i + 1), normal, grip, tint, light);
            vertex(consumer, pose, polygon.get(Math.min(i + 2, last)), normal, grip, tint, light);
        }
    }

    private static List<Corner> clip(List<Corner> polygon, float bound, boolean keepAbove) {
        List<Corner> kept = new ArrayList<>(polygon.size() + 2);
        for (int i = 0; i < polygon.size(); i++) {
            Corner from = polygon.get(i);
            Corner to = polygon.get((i + 1) % polygon.size());
            boolean fromKept = keepAbove ? from.y() >= bound : from.y() <= bound;
            boolean toKept = keepAbove ? to.y() >= bound : to.y() <= bound;
            if (fromKept) {
                kept.add(from);
            }
            if (fromKept != toKept) {
                kept.add(from.towards(to, (bound - from.y()) / (to.y() - from.y())));
            }
        }
        return kept;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Corner corner, Vector3f normal,
                               float grip, Tint tint, int light) {
        Vector4f at = pose.pose().transform(new Vector4f(corner.x(), corner.y(), corner.z(), 1.0F));
        int argb = tint.argb(Math.abs(corner.y() - grip));
        consumer.vertex(at.x(), at.y(), at.z(),
            FastColor.ARGB32.red(argb) / 255.0F, FastColor.ARGB32.green(argb) / 255.0F,
            FastColor.ARGB32.blue(argb) / 255.0F, FastColor.ARGB32.alpha(argb) / 255.0F,
            corner.u(), corner.v(), OverlayTexture.NO_OVERLAY, light, normal.x(), normal.y(), normal.z());
    }

    private static List<Face> facesOf(BakedModel model) {
        List<Face> faces = new ArrayList<>();
        RandomSource random = RandomSource.create();
        for (Direction direction : DIRECTIONS) {
            random.setSeed(QUAD_SEED);
            for (BakedQuad quad : model.getQuads(null, direction, random)) {
                faces.add(Face.of(quad));
            }
        }
        random.setSeed(QUAD_SEED);
        for (BakedQuad quad : model.getQuads(null, null, random)) {
            faces.add(Face.of(quad));
        }
        return faces;
    }

    private static boolean inHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private static InteractionHand handOf(LivingEntity holder, boolean leftHand) {
        HumanoidArm arm = leftHand ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        return arm == holder.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static long key(int entityId, InteractionHand hand) {
        return ((long) entityId << 1) | hand.ordinal();
    }

    private static float phase(float value, float from, float to) {
        return to - from <= EPSILON ? 1.0F : Mth.clamp((value - from) / (to - from), 0.0F, 1.0F);
    }

    private static float easeOut(float t) {
        float rest = 1.0F - t;
        return 1.0F - rest * rest;
    }

    private static int scaled(int rgb, float strength) {
        return FastColor.ARGB32.color(0,
            (int) (FastColor.ARGB32.red(rgb) * strength),
            (int) (FastColor.ARGB32.green(rgb) * strength),
            (int) (FastColor.ARGB32.blue(rgb) * strength));
    }

    private static int brightened(int rgb) {
        int peak = Math.max(FastColor.ARGB32.red(rgb), Math.max(FastColor.ARGB32.green(rgb), FastColor.ARGB32.blue(rgb)));
        return peak == 0 ? rgb : scaled(rgb, 255.0F / peak);
    }

    private static int withAlpha(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int grey(float strength) {
        int level = Mth.clamp((int) (strength * 255.0F), 0, 255);
        return FastColor.ARGB32.color(255, level, level, level);
    }

    @FunctionalInterface
    private interface Tint {
        int argb(float distance);
    }

    private record Stage(float grip, float length, float solid, float energy, float afterglowFrom,
                         float afterglowAlpha) {
        static Stage at(float progress, float grip, float length) {
            float front = (1.0F + ENERGY_REACH) * easeOut(phase(progress, 0.0F, FORMED_AT));
            float solid = length * Mth.clamp(front - ENERGY_REACH, 0.0F, 1.0F);
            return new Stage(grip, length, solid,
                length * Math.min(front, 1.0F),
                Math.max(0.0F, solid - length * AFTERGLOW_REACH),
                AFTERGLOW_ALPHA * (1.0F - phase(progress, FORMED_AT, 1.0F)));
        }

        float drawn() {
            return this.solid >= this.length - EPSILON ? Float.MAX_VALUE : this.solid;
        }
    }

    private record Materialisation(long start, boolean arriving, Item item) {
        float age(float partialTick) {
            return clientTicks - this.start + partialTick;
        }
    }

    private record Corner(float x, float y, float z, float u, float v) {
        Corner towards(Corner other, float t) {
            return new Corner(Mth.lerp(t, this.x, other.x), Mth.lerp(t, this.y, other.y),
                Mth.lerp(t, this.z, other.z), Mth.lerp(t, this.u, other.u), Mth.lerp(t, this.v, other.v));
        }
    }

    private record Face(List<Corner> corners, float normalX, float normalY, float normalZ, boolean animated) {
        static Face of(BakedQuad quad) {
            int[] data = quad.getVertices();
            List<Corner> corners = new ArrayList<>(4);
            for (int base = 0; base + STRIDE <= data.length; base += STRIDE) {
                corners.add(new Corner(Float.intBitsToFloat(data[base]), Float.intBitsToFloat(data[base + 1]),
                    Float.intBitsToFloat(data[base + 2]), Float.intBitsToFloat(data[base + U_OFFSET]),
                    Float.intBitsToFloat(data[base + U_OFFSET + 1])));
            }
            Vec3i normal = quad.getDirection().getNormal();
            return new Face(corners, normal.getX(), normal.getY(), normal.getZ(),
                quad.getSprite().contents().getUniqueFrames().count() > 1);
        }
    }
}
