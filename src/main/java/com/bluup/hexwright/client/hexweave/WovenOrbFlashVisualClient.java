package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.mixin.GameRendererAccessor;
import com.bluup.hexwright.server.hexweave.WovenOrbCasting;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Color;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class WovenOrbFlashVisualClient {

    private static final ResourceLocation ORB_USE_FX = Hexwright.id("orb_use");

    private static final double FIRST_PERSON_FORWARD = 0.72;
    private static final double FIRST_PERSON_SIDE = 0.56;
    private static final double FIRST_PERSON_DOWN = 0.52 - 3.0 / 16.0;
    private static final double THIRD_PERSON_HEIGHT = 0.42;
    private static final double THIRD_PERSON_FORWARD = 0.3;
    private static final double THIRD_PERSON_SIDE = 0.36;

    private static boolean warnedMissingFx;

    private WovenOrbFlashVisualClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WovenOrbCasting.FIRED_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            InteractionHand hand = buf.readEnum(InteractionHand.class);
            client.execute(() -> play(client, entityId, hand));
        });
    }

    private static void play(Minecraft client, int entityId, InteractionHand hand) {
        if (client.level == null || !(client.level.getEntity(entityId) instanceof Player holder)) {
            return;
        }
        FX base = FXHelper.getFX(ORB_USE_FX);
        if (base == null) {
            if (!warnedMissingFx) {
                warnedMissingFx = true;
                Hexwright.LOGGER.warn("Woven Orb flash effect {} failed to load", ORB_USE_FX);
            }
            return;
        }

        float partialTick = client.getFrameTime();
        Vec3 orb = orbPosition(client, holder, hand, partialTick);
        Integer pigment = pigmentColour(holder, orb);
        try {
            EntityEffect effect = new OrbFlash(pigment == null ? base : tinted(base, pigment), holder, hand);
            effect.setOffset(orb.subtract(holder.getPosition(partialTick)).toVector3f());
            effect.setAllowMulti(true);
            effect.start();
        } catch (RuntimeException e) {
            Hexwright.LOGGER.warn("Woven Orb flash effect failed to play on {}", holder.getScoreboardName(), e);
        }
    }

    private static final class OrbFlash extends EntityEffect {
        private final Player holder;
        private final InteractionHand hand;

        OrbFlash(FX fx, Player holder, InteractionHand hand) {
            super(fx, holder.level(), holder, AutoRotate.NONE);
            this.holder = holder;
            this.hand = hand;
        }

        @Override
        public void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {
            if (runtime != null && fxObject == runtime.getRoot()) {
                fxObject.updatePos(orbPosition(Minecraft.getInstance(), holder, hand, partialTicks).toVector3f());
            }
        }
    }

    private static Vec3 orbPosition(Minecraft client, Player holder, InteractionHand hand, float partialTick) {
        boolean rightSide = (hand == InteractionHand.MAIN_HAND) == (holder.getMainArm() == HumanoidArm.RIGHT);
        double side = rightSide ? 1.0 : -1.0;
        if (holder == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) {
            Vec3 look = holder.getViewVector(partialTick);
            Vec3 up = holder.getUpVector(partialTick);
            Vec3 right = look.cross(up);
            double spread = handToWorldSpread(client, partialTick);
            return holder.getEyePosition(partialTick)
                .add(look.scale(FIRST_PERSON_FORWARD))
                .add(right.scale(side * FIRST_PERSON_SIDE * spread))
                .subtract(up.scale(FIRST_PERSON_DOWN * spread));
        }
        float yaw = Mth.lerp(partialTick, holder.yBodyRotO, holder.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        return holder.getPosition(partialTick)
            .add(0.0, holder.getBbHeight() * THIRD_PERSON_HEIGHT, 0.0)
            .add(forward.scale(THIRD_PERSON_FORWARD))
            .add(right.scale(side * THIRD_PERSON_SIDE));
    }

    private static double handToWorldSpread(Minecraft client, float partialTick) {
        GameRendererAccessor renderer = (GameRendererAccessor) client.gameRenderer;
        Camera camera = client.gameRenderer.getMainCamera();
        double world = renderer.hexwright$getFov(camera, partialTick, true);
        double hand = renderer.hexwright$getFov(camera, partialTick, false);
        return Math.tan(Math.toRadians(world) / 2.0) / Math.tan(Math.toRadians(hand) / 2.0);
    }

    private static @Nullable Integer pigmentColour(Player holder, Vec3 at) {
        FrozenPigment pigment = IXplatAbstractions.INSTANCE.getPigment(holder);
        if (pigment == null || pigment.item().is(HexItems.DEFAULT_PIGMENT)) {
            return null;
        }
        try {
            return IXplatAbstractions.INSTANCE.getColorProvider(pigment).getColor(holder.tickCount, at) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static FX tinted(FX base, int rgb) {
        FX copy = new FX();
        copy.deserializeNBT(base.serializeNBT());
        copy.setFxLocation(base.getFxLocation());
        for (IFXObject object : copy.getMainFX().objects()) {
            if (object instanceof ParticleEmitter emitter && emitter.config.getStartColor() instanceof Constant authored) {
                int alpha = authored.getNumber().intValue() & 0xFF000000;
                emitter.config.setStartColor(new Color(alpha | rgb));
            }
        }
        return copy;
    }
}
