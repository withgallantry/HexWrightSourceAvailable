package com.bluup.hexwright.client.vehicle;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.vehicle.BroomEntity;
import com.bluup.hexwright.server.vehicle.VehicleConfig;
import com.bluup.hexwright.server.vehicle.VehicleMovementMath;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class BroomTrailVisualClient {
    private static final ResourceLocation TRAIL_FX = Hexwright.id("broom");

    private static final Map<BroomEntity, EntityEffect> TRACKED = new HashMap<>();

    private BroomTrailVisualClient() {
    }

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof BroomEntity broom)) {
                return;
            }
            FX fx = FXHelper.getFX(TRAIL_FX);
            if (fx != null) {
                TRACKED.put(broom, new EntityEffect(fx, level, broom, EntityEffect.AutoRotate.NONE));
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof BroomEntity broom) {
                stop(TRACKED.remove(broom), true);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Iterator<Map.Entry<BroomEntity, EntityEffect>> it = TRACKED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BroomEntity, EntityEffect> entry = it.next();
                BroomEntity broom = entry.getKey();
                if (!broom.isAlive()) {
                    it.remove();
                    continue;
                }
                tickTrail(broom, entry.getValue());
            }
        });
    }

    private static void tickTrail(BroomEntity broom, EntityEffect effect) {
        Vec3 v = broom.getDeltaMovement();
        boolean moving = v.x * v.x + v.z * v.z >= VehicleConfig.BROOM_TRAIL_MIN_SPEED_SQ;
        if (!moving) {
            stop(effect, false);
            return;
        }

        Quaternionf rotation = worldRotation(broom);
        effect.setOffset(worldOffset(broom));
        effect.setRotation(rotation);

        FXRuntime runtime = effect.getRuntime();
        if (runtime == null || !runtime.isAlive()) {
            try {
                effect.start();
            } catch (RuntimeException ignored) {
            }
            runtime = effect.getRuntime();
        }
        if (runtime != null && runtime.isAlive()) {
            runtime.getRoot().updateRotation(rotation);
        }
    }

    private static Vector3f worldOffset(BroomEntity broom) {
        Vec3 forward = VehicleMovementMath.horizontalForward(broom.getYRot());
        Vec3 right = VehicleMovementMath.horizontalRight(forward);
        Vec3 offset = right.scale(VehicleConfig.BROOM_TRAIL_OFFSET_SIDEWAYS)
            .add(forward.scale(-VehicleConfig.BROOM_TRAIL_OFFSET_BACK))
            .add(0.0, VehicleConfig.BROOM_TRAIL_OFFSET_UP, 0.0);
        return new Vector3f((float) offset.x, (float) offset.y, (float) offset.z);
    }

    private static Quaternionf worldRotation(BroomEntity broom) {
        Vec3 backward = VehicleMovementMath.horizontalForward(broom.getYRot()).scale(-1.0);
        return new Quaternionf().rotationTo(
            new Vector3f(0.0f, 1.0f, 0.0f),
            new Vector3f((float) backward.x, 0.0f, (float) backward.z)
        );
    }

    private static void stop(EntityEffect effect, boolean forced) {
        if (effect == null) {
            return;
        }
        FXRuntime runtime = effect.getRuntime();
        if (runtime != null && runtime.isAlive()) {
            runtime.destroy(forced);
        }
    }
}
