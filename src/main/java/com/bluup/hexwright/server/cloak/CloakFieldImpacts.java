package com.bluup.hexwright.server.cloak;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CloakFieldImpacts {

    private static final double BROADCAST_RANGE = 160.0;
    private static final double CONTACT_RANGE = 1.5;
    private static final long REPEAT_TICKS = 8;
    private static final double REPEAT_DISTANCE_SQ = 2.0 * 2.0;

    private record LastImpact(Vec3 point, long gameTime) {
    }

    private static final Map<UUID, LastImpact> LAST = new HashMap<>();

    private CloakFieldImpacts() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(CloakFieldImpacts::detectContacts);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!CloakFieldShell.isBarrier(level.getBlockState(pos))) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel serverLevel) {
                Vec3 point = Vec3.atCenterOf(pos).relative(direction, 0.5);
                CloakField field = impact(serverLevel, point);
                if (field != null) {
                    CloakFieldWear.strike(serverLevel, field, point, player);
                }
            }
            return InteractionResult.SUCCESS;
        });
    }

    public static @Nullable CloakField impact(ServerLevel level, Vec3 point) {
        CloakField nearest = null;
        double best = Double.MAX_VALUE;
        for (CloakField field : CloakFieldStore.fieldsIn(level)) {
            double distanceSq = field.surfacePoint(point).distanceToSqr(point);
            if (distanceSq < best) {
                best = distanceSq;
                nearest = field;
            }
        }
        if (nearest != null) {
            ripple(level, nearest, nearest.surfacePoint(point));
        }
        return nearest;
    }

    private static void detectContacts(ServerLevel level) {
        for (CloakField field : CloakFieldStore.fieldsIn(level)) {
            Vec3 c = field.center();
            double r = field.radius();
            AABB around = new AABB(c.x - r, c.y, c.z - r, c.x + r, c.y + field.height(), c.z + r)
                .inflate(CONTACT_RANGE + 1.0);
            for (Entity entity : level.getEntities((Entity) null, around, Entity::isAlive)) {
                if (!entity.horizontalCollision) {
                    continue;
                }
                Vec3 middle = entity.getBoundingBox().getCenter();
                Vec3 onSurface = field.surfacePoint(middle);
                if (onSurface.distanceTo(middle) - entity.getBbWidth() * 0.5 <= CONTACT_RANGE) {
                    ripple(level, field, onSurface);
                }
            }
        }
    }

    static void ripple(ServerLevel level, CloakField field, Vec3 point) {
        long now = level.getGameTime();
        LastImpact last = LAST.get(field.id());
        if (last != null && now - last.gameTime() < REPEAT_TICKS
            && last.point().distanceToSqr(point) < REPEAT_DISTANCE_SQ) {
            return;
        }
        LAST.put(field.id(), new LastImpact(point, now));
        for (ServerPlayer player : PlayerLookup.around(level, point, BROADCAST_RANGE)) {
            HexwrightNetworking.sendCloakRipple(player, field.id(), point);
        }
    }
}
