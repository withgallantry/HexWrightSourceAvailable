package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class WeaponTrailVisualClient {

    private static final Map<Integer, Trail> ACTIVE = new HashMap<>();

    private static final Map<ResourceLocation, Boolean> WARNED = new HashMap<>();

    private WeaponTrailVisualClient() {
    }

    public static void onParticlesCleared() {
        ACTIVE.clear();
    }

    public static void onSwingStarted(AbstractClientPlayer player, int durationTicks) {
        Item item = player.getMainHandItem().getItem();
        if (!(item instanceof AnimatedWeapon weapon)) {
            return;
        }
        ResourceLocation fxId = weapon.trailFx();
        if (fxId == null || WeaponModelAnchors.get(item) == null) {
            return;
        }

        stop(ACTIVE.remove(player.getId()), true);
        ACTIVE.put(player.getId(), new Trail(player, item, fxId, durationTicks));
    }

    public static void onSwingStopped(AbstractClientPlayer player) {
        stop(ACTIVE.remove(player.getId()), false);
    }

    public static void onWeaponRendered(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                        HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer) {
        if (context.firstPerson()) {
            return;
        }
        Trail trail = ACTIVE.get(entity.getId());
        if (trail != null && (trail.player != entity || stack.getItem() != trail.item)) {
            trail = null;
        }
        boolean marker = WeaponTrailTuner.isTuning(stack.getItem());
        if (trail == null && !marker) {
            return;
        }

        Vector3f anchor = WeaponModelAnchors.get(stack.getItem());
        if (anchor == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Vec3 world = toWorld(mc, entity, stack, context, arm, poseStack, anchor, marker ? buffer : null);
        if (world == null || trail == null) {
            return;
        }

        Vec3 origin = entity.getPosition(mc.getFrameTime());
        trail.place(new Vector3f(
            (float) (world.x - origin.x),
            (float) (world.y - origin.y),
            (float) (world.z - origin.z)));
    }

    public static void onClientTick(Minecraft client) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Trail>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Trail trail = it.next().getValue();
            if (trail.expired(client)) {
                stop(trail, false);
                it.remove();
            }
        }
    }

    @Nullable
    private static Vec3 toWorld(Minecraft mc, LivingEntity entity, ItemStack stack,
                                ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack,
                                Vector3f anchor, @Nullable MultiBufferSource marker) {
        BakedModel model = mc.getItemRenderer()
            .getModel(stack, entity.level(), entity, entity.getId() + context.ordinal());
        if (model == null) {
            return null;
        }

        Vector3f point = new Vector3f(anchor);
        poseStack.pushPose();
        try {
            model.getTransforms().getTransform(context).apply(arm == HumanoidArm.LEFT, poseStack);
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            if (marker != null) {
                WeaponTrailTuner.renderMarker(poseStack, marker, anchor);
            }
            point.mulPosition(poseStack.last().pose());
        } finally {
            poseStack.popPose();
        }

        point.mul(RenderSystem.getInverseViewRotationMatrix());
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        return camera.add(point.x, point.y, point.z);
    }

    private static void stop(@Nullable Trail trail, boolean forced) {
        if (trail == null) {
            return;
        }
        trail.stop(forced);
    }

    private static final class Trail {
        private final AbstractClientPlayer player;
        private final Item item;
        private final ResourceLocation fxId;

        private int ticksLeft;

        @Nullable
        private EntityEffect effect;

        private Trail(AbstractClientPlayer player, Item item, ResourceLocation fxId, int durationTicks) {
            this.player = player;
            this.item = item;
            this.fxId = fxId;
            this.ticksLeft = durationTicks;
        }

        private void place(Vector3f offset) {
            if (effect == null) {
                FX fx = FXHelper.getFX(fxId);
                if (fx == null) {
                    if (WARNED.putIfAbsent(fxId, Boolean.TRUE) == null) {
                        Hexwright.LOGGER.warn("Weapon trail effect {} failed to load", fxId);
                    }
                    ticksLeft = 0;
                    return;
                }
                try {
                    effect = new EntityEffect(fx, player.level(), player, EntityEffect.AutoRotate.NONE);
                    effect.setOffset(offset);
                    effect.start();
                } catch (RuntimeException e) {
                    Hexwright.LOGGER.warn("Weapon trail effect {} failed to play", fxId, e);
                    effect = null;
                    ticksLeft = 0;
                }
                return;
            }
            effect.setOffset(offset);
        }

        private boolean expired(Minecraft client) {
            if (--ticksLeft <= 0
                || !player.isAlive()
                || player.level() != client.level
                || player.getMainHandItem().getItem() != item) {
                return true;
            }
            FXRuntime runtime = effect == null ? null : effect.getRuntime();
            return effect != null && (runtime == null || !runtime.isAlive());
        }

        private void stop(boolean forced) {
            if (effect == null) {
                return;
            }
            try {
                FXRuntime runtime = effect.getRuntime();
                if (runtime != null && runtime.isAlive()) {
                    runtime.destroy(forced);
                }
            } catch (RuntimeException ignored) {
            }
            effect = null;
        }
    }
}
