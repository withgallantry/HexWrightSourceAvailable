package com.bluup.hexwright.client.staff_assembly;

import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.render.emissive.EmissiveBloom;
import com.bluup.hexwright.client.render.emissive.EmissiveGlowLayer;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.server.item.ConfigurableStaffItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class StaffTipFlash {
    private static final ResourceLocation SPRITE = Hexwright.id("effect/staff_tip_flash");

    private static final float ATTACK_TICKS = 2.0f;

    private static final float BLOOM_OUT = 0.35f;

    private static final float ATTACK_FROM = 0.45f;

    private static final float MARKER_REACH = 0.09f;

    private static final int[] MARKER_COLORS = {0xFFFF4040, 0xFF40FF40, 0xFF4080FF};

    private static final float DIM_SIZE_FLOOR = 0.6f;
    private static final float DIM_ALPHA_FLOOR = 0.35f;
    private static final float DIM_DECAY_FLOOR = 0.6f;

    private static final int NO_HOLDER = Integer.MIN_VALUE;

    private static boolean enabled = true;
    private static boolean marker;

    private static float size = 0.22f;
    private static float decayTicks = 7.0f;
    private static int color = 0xD7C0FF;

    private static long clientTicks;

    private static final Map<Integer, Flash> FLASHES = new HashMap<>();

    @Nullable
    private static Entity holder;

    private StaffTipFlash() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTicks++;
            if (client.level == null) {
                FLASHES.clear();
                return;
            }
            long stale = (long) Math.ceil(ATTACK_TICKS + decayTicks) + 1L;
            FLASHES.values().removeIf(flash -> clientTicks - flash.start() > stale);
        });
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            FLASHES.clear();
        }
    }

    public static boolean isMarker() {
        return marker;
    }

    public static void setMarker(boolean value) {
        marker = value;
    }

    public static float size() {
        return size;
    }

    public static void setSize(float value) {
        size = value;
    }

    public static float decay() {
        return decayTicks;
    }

    public static void setDecay(float value) {
        decayTicks = value;
    }

    public static int color() {
        return color;
    }

    public static void setColor(int value) {
        color = value;
    }

    public static void flash() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            flash(client.player.getId(), color, 1.0f);
        }
    }

    public static void flash(int entityId, int rgb, float intensity) {
        FLASHES.put(entityId, new Flash(clientTicks, rgb, Mth.clamp(intensity, 0.0f, 1.0f)));
    }

    public static void handleFlash(int entityId, float intensity, @Nullable CompoundTag pigmentTag) {
        if (!enabled) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Entity caster = client.level != null ? client.level.getEntity(entityId) : null;
        flash(entityId, resolveColor(pigmentTag, caster), intensity);
    }

    private static int resolveColor(@Nullable CompoundTag pigmentTag, @Nullable Entity caster) {
        if (pigmentTag == null) {
            return color;
        }
        try {
            FrozenPigment pigment = FrozenPigment.fromNBT(pigmentTag);
            ColorProvider provider = IXplatAbstractions.INSTANCE.getColorProvider(pigment);
            return provider.getColor(clientTicks, caster != null ? caster.position() : Vec3.ZERO) & 0xFFFFFF;
        } catch (RuntimeException ignored) {
            return color;
        }
    }

    public static void beginHolder(Entity entity) {
        holder = entity;
    }

    public static void endHolder() {
        holder = null;
    }

    public static void onStaffRendered(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                       MultiBufferSource buffers, RenderType itemLayer) {
        if (!enabled || !inHand(context) || !(stack.getItem() instanceof ConfigurableStaffItem)) {
            return;
        }

        Vector3f anchor = StaffTipAnchors.get(StaffParts.modelId(stack));
        if (anchor == null) {
            return;
        }

        if (marker) {
            drawMarker(buffers, poseStack, anchor);
        }

        int entityId = holderId(context);
        Flash flash = entityId == NO_HOLDER ? null : FLASHES.get(entityId);
        if (flash == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        float age = (clientTicks - flash.start()) + client.getFrameTime();
        if (age < 0.0f) {
            return;
        }

        float decay = Math.max(0.001f, decayTicks * fromFloor(DIM_DECAY_FLOOR, flash.intensity()));

        float alpha;
        float scale;
        if (age < ATTACK_TICKS) {
            float t = age / ATTACK_TICKS;
            alpha = t;
            scale = ATTACK_FROM + (1.0f - ATTACK_FROM) * t;
        } else {
            float t = (age - ATTACK_TICKS) / decay;
            if (t >= 1.0f) {
                FLASHES.remove(entityId);
                return;
            }
            alpha = (1.0f - t) * (1.0f - t);
            scale = 1.0f + BLOOM_OUT * t;
        }

        float half = size * scale * fromFloor(DIM_SIZE_FLOOR, flash.intensity());
        drawFlash(client, buffers, poseStack, itemLayer, anchor, flash.rgb(),
            alpha * fromFloor(DIM_ALPHA_FLOOR, flash.intensity()), half);
    }

    private static float fromFloor(float floor, float intensity) {
        return floor + (1.0f - floor) * intensity;
    }

    private static int holderId(ItemDisplayContext context) {
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null ? player.getId() : NO_HOLDER;
        }
        return holder != null ? holder.getId() : NO_HOLDER;
    }

    private static boolean inHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private static void drawFlash(Minecraft client, MultiBufferSource buffers, PoseStack poseStack,
                                  RenderType itemLayer, Vector3f anchor, int rgb, float alpha,
                                  float half) {
        TextureAtlasSprite sprite = client.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(SPRITE);
        RenderType layer = EmissiveGlowLayer.maskedLayer();

        Matrix3f screen = new Matrix3f(poseStack.last().pose());
        if (Math.abs(screen.determinant()) < 1.0e-9f) {
            return;
        }
        screen.invert();

        Vector3f right = screen.transform(new Vector3f(half, 0.0f, 0.0f));
        Vector3f up = screen.transform(new Vector3f(0.0f, half, 0.0f));
        Vector3f towards = screen.transform(new Vector3f(0.0f, 0.0f, 1.0f)).normalize();

        float red = ((rgb >> 16) & 0xFF) / 255.0f;
        float green = ((rgb >> 8) & 0xFF) / 255.0f;
        float blue = (rgb & 0xFF) / 255.0f;

        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(itemLayer);
        }
        PoseStack.Pose pose = poseStack.last();
        emitQuad(buffers.getBuffer(layer), pose, sprite, anchor, right, up, towards,
            red, green, blue, alpha);

        EmissiveBloom.capture(layer, consumer ->
            emitQuad(consumer, pose, sprite, anchor, right, up, towards, red, green, blue, alpha));
    }

    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite,
                                 Vector3f anchor, Vector3f right, Vector3f up, Vector3f towards,
                                 float red, float green, float blue, float alpha) {
        corner(consumer, pose, anchor, right, up, towards, -1, -1, sprite.getU0(), sprite.getV1(),
            red, green, blue, alpha);
        corner(consumer, pose, anchor, right, up, towards, 1, -1, sprite.getU1(), sprite.getV1(),
            red, green, blue, alpha);
        corner(consumer, pose, anchor, right, up, towards, 1, 1, sprite.getU1(), sprite.getV0(),
            red, green, blue, alpha);
        corner(consumer, pose, anchor, right, up, towards, -1, 1, sprite.getU0(), sprite.getV0(),
            red, green, blue, alpha);
    }

    private static void corner(VertexConsumer consumer, PoseStack.Pose pose, Vector3f anchor,
                               Vector3f right, Vector3f up, Vector3f towards, int sx, int sy,
                               float u, float v, float red, float green, float blue, float alpha) {
        consumer.vertex(pose.pose(),
                anchor.x + right.x * sx + up.x * sy,
                anchor.y + right.y * sx + up.y * sy,
                anchor.z + right.z * sx + up.z * sy)
            .color(red, green, blue, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(pose.normal(), towards.x, towards.y, towards.z)
            .endVertex();
    }

    private static void drawMarker(MultiBufferSource buffers, PoseStack poseStack, Vector3f anchor) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        for (int axis = 0; axis < 3; axis++) {
            float dx = axis == 0 ? MARKER_REACH : 0.0f;
            float dy = axis == 1 ? MARKER_REACH : 0.0f;
            float dz = axis == 2 ? MARKER_REACH : 0.0f;
            arm(lines, pose, normal, anchor, -dx, -dy, -dz, MARKER_COLORS[axis]);
            arm(lines, pose, normal, anchor, dx, dy, dz, MARKER_COLORS[axis]);
        }
    }

    private static void arm(VertexConsumer lines, Matrix4f pose, Matrix3f normal, Vector3f anchor,
                            float dx, float dy, float dz, int rgba) {
        lines.vertex(pose, anchor.x + dx, anchor.y + dy, anchor.z + dz)
            .color(rgba)
            .normal(normal, Math.signum(dx), Math.signum(dy), Math.signum(dz))
            .endVertex();
    }

    private record Flash(long start, int rgb, float intensity) {
    }

    @Nullable
    public static Vector3f heldAnchor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        ItemStack held = client.player.getMainHandItem();
        if (!(held.getItem() instanceof ConfigurableStaffItem)) {
            return null;
        }
        return StaffTipAnchors.get(StaffParts.modelId(held));
    }
}
