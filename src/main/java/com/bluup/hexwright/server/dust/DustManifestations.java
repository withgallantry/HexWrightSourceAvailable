package com.bluup.hexwright.server.dust;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.region.Region;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class DustManifestations {

    private static final Map<UUID, DustBody> ACTIVE = new HashMap<>();

    private DustManifestations() {
    }

    public static void register() {
        EntityTrackingEvents.START_TRACKING.register((tracked, player) -> {
            DustBody body = ACTIVE.get(tracked.getUUID());
            if (body != null && body.caster == tracked) {
                HexwrightNetworking.sendDust(player, body.statePacket());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> release(entity));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.player.getUUID()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            DustBody body = ACTIVE.get(player.getUUID());
            if (body != null) {
                body.changedWorld();
                HexwrightNetworking.sendDust(player, body.statePacket());
            }
        });
        DustSupport.source(false, into -> {
            for (DustBody body : ACTIVE.values()) {
                DustSupport.Surface surface = body.surface();
                if (surface != null) {
                    into.add(surface);
                }
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> DustSupport.invalidate(false));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<DustBody> it = ACTIVE.values().iterator();
            while (it.hasNext()) {
                DustBody body = it.next();
                LivingEntity caster = body.caster;
                if (caster.isRemoved()) {
                    if (!(caster instanceof ServerPlayer)) {
                        it.remove();
                    }
                    continue;
                }
                if (caster.level() instanceof ServerLevel level) {
                    body.tick(level);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear();
            DustSupport.clear(false);
        });
    }

    public static @Nullable DustBody body(LivingEntity caster) {
        DustBody body = ACTIVE.get(caster.getUUID());
        return body != null && body.caster == caster ? body : null;
    }

    public static boolean isActive(LivingEntity caster) {
        return body(caster) != null;
    }

    public static boolean isFormed(LivingEntity caster) {
        DustBody body = body(caster);
        return body != null && body.formation != null;
    }

    public static double mass(LivingEntity caster) {
        DustBody body = body(caster);
        return body == null ? 0.0 : body.mass;
    }

    public static void manifest(LivingEntity caster, double targetMass) {
        if (targetMass <= 0.0) {
            release(caster);
            return;
        }
        DustBody body = body(caster);
        if (body == null) {
            body = new DustBody(caster, targetMass);
            ACTIVE.put(caster.getUUID(), body);
            body.syncState();
        } else {
            body.setTarget(targetMass);
        }
    }

    public static void direct(LivingEntity caster, Vec3 vector) {
        DustBody body = body(caster);
        if (body != null) {
            body.direct(vector);
        }
    }

    public static void form(LivingEntity caster, Region region) {
        DustBody body = body(caster);
        if (body != null) {
            body.form(region);
        }
    }

    public static void constrain(LivingEntity caster, boolean constrained) {
        DustBody body = body(caster);
        if (body != null) {
            body.setConstrained(constrained);
        }
    }

    public static void recall(LivingEntity caster) {
        DustBody body = body(caster);
        if (body != null) {
            body.recall();
        }
    }

    private static void release(LivingEntity caster) {
        if (body(caster) == null) {
            return;
        }
        ACTIVE.remove(caster.getUUID());
        HexwrightNetworking.broadcastDust(caster, DustPacket.release(caster.getId()));
    }
}
