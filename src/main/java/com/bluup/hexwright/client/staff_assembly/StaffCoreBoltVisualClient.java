package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.staff_assembly.StaffCoreBoltEntity;
import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class StaffCoreBoltVisualClient {
    private static final ResourceLocation BOLT_FX = Hexwright.id("projectile");
    private static final ResourceLocation HIT_FX = Hexwright.id("projectile_hit");

    private StaffCoreBoltVisualClient() {
    }

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof StaffCoreBoltEntity bolt) {
                playBoltFX(bolt);
            }
        });
    }

    public static void handleHit(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        FX fx = FXHelper.getFX(HIT_FX);
        if (fx == null) {
            return;
        }

        BlockPos anchor = new BlockPos(Mth.floor(point.x), Mth.floor(point.y), Mth.floor(point.z));
        try {
            BlockEffect effect = new BlockEffect(fx, level, anchor);
            effect.setOffset(new Vector3f(
                (float) (point.x - anchor.getX()),
                (float) (point.y - anchor.getY()),
                (float) (point.z - anchor.getZ())
            ));
            effect.start();
        } catch (RuntimeException ignored) {
        }
    }

    private static void playBoltFX(StaffCoreBoltEntity bolt) {
        FX fx = FXHelper.getFX(BOLT_FX);
        if (fx == null) {
            return;
        }

        try {
            EntityEffect effect = new EntityEffect(fx, bolt.level(), bolt, EntityEffect.AutoRotate.NONE);
            effect.setOffset(new Vector3f(0.0f, bolt.getBbHeight() * 0.5f, 0.0f));
            effect.setRotation(headingRotation(bolt));
            effect.setForcedDeath(true);
            effect.start();
        } catch (RuntimeException ignored) {
        }
    }

    private static Quaternionf headingRotation(StaffCoreBoltEntity bolt) {
        Vec3 heading = firedHeading(bolt);
        float yaw = (float) Mth.atan2(-heading.z, heading.x);
        float pitch = (float) Math.asin(Mth.clamp(heading.y, -1.0, 1.0));
        return new Quaternionf().rotateY(yaw).rotateZ(pitch);
    }

    private static Vec3 firedHeading(StaffCoreBoltEntity bolt) {
        double yaw = Math.toRadians(bolt.getYRot());
        double pitch = Math.toRadians(bolt.getXRot());
        double horizontal = Math.cos(pitch);
        return new Vec3(Math.sin(yaw) * horizontal, Math.sin(pitch), Math.cos(yaw) * horizontal);
    }
}
