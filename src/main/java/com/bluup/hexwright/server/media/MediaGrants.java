package com.bluup.hexwright.server.media;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import at.petrak.hexcasting.api.casting.eval.env.CircleCastEnv;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.particles.ConjureParticleOptions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class MediaGrants {

    private static final int PARTICLE_INTERVAL = 4;
    private static final int PARTICLE_MOTES = 2;

    private static final int GRANT_COLOR = 0xFF_B0_7C_F0;

    private static final Map<GlobalPos, Long> BLOCK_GRANTS = new HashMap<>();

    private static final List<WeakReference<Entity>> ENTITY_ROSTER = new ArrayList<>();

    private static @Nullable MinecraftServer server = null;
    private static int tickCounter = 0;

    private MediaGrants() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerTickEvents.END_SERVER_TICK.register(ignored -> {
            if (++tickCounter % PARTICLE_INTERVAL == 0) {
                emitParticles();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            BLOCK_GRANTS.clear();
            ENTITY_ROSTER.clear();
            server = null;
        });
    }


    public static void grant(Entity entity, long media) {
        if (!(entity instanceof MediaGrantHolder holder)) {
            return;
        }
        holder.hexwright$setMediaGrant(holder.hexwright$mediaGrant() + media);
        enrol(entity);
    }

    public static void grant(ServerLevel level, BlockPos pos, long media) {
        BLOCK_GRANTS.merge(GlobalPos.of(level.dimension(), pos.immutable()), media, Long::sum);
    }

    public static long remaining(Entity entity) {
        return entity instanceof MediaGrantHolder holder ? holder.hexwright$mediaGrant() : 0L;
    }

    public static long remaining(ServerLevel level, BlockPos pos) {
        return BLOCK_GRANTS.getOrDefault(GlobalPos.of(level.dimension(), pos.immutable()), 0L);
    }

    private static void enrol(Entity entity) {
        for (WeakReference<Entity> ref : ENTITY_ROSTER) {
            if (ref.get() == entity) {
                return;
            }
        }
        ENTITY_ROSTER.add(new WeakReference<>(entity));
    }


    private static final CastingEnvironmentComponent.Key<CastingEnvironmentComponent.ExtractMedia.Pre>
        GRANT_KEY = new CastingEnvironmentComponent.Key<>() {
    };

    public static void install(CastingEnvironment env) {
        env.addExtension(new CastingEnvironmentComponent.ExtractMedia.Pre() {
            @Override
            public CastingEnvironmentComponent.Key<?> getKey() {
                return GRANT_KEY;
            }

            @Override
            public long onExtractMedia(long cost, boolean simulate) {
                return pay(env, cost, simulate);
            }
        });
    }

    public static long pay(CastingEnvironment env, long cost, boolean simulate) {
        if (cost <= 0) {
            return cost;
        }

        BlockPos pos = grantPos(env);
        if (pos != null) {
            GlobalPos key = GlobalPos.of(env.getWorld().dimension(), pos);
            long held = BLOCK_GRANTS.getOrDefault(key, 0L);
            long paid = Math.min(cost, held);
            if (!simulate && paid > 0) {
                long left = held - paid;
                if (left <= 0) {
                    BLOCK_GRANTS.remove(key);
                } else {
                    BLOCK_GRANTS.put(key, left);
                }
            }
            return cost - paid;
        }

        Entity entity = grantEntity(env);
        if (!(entity instanceof MediaGrantHolder holder)) {
            return cost;
        }
        long held = holder.hexwright$mediaGrant();
        long paid = Math.min(cost, held);
        if (!simulate && paid > 0) {
            holder.hexwright$setMediaGrant(held - paid);
        }
        return cost - paid;
    }


    private static @Nullable BlockPos grantPos(CastingEnvironment env) {
        if (env instanceof MediaGrantOwner owner) {
            BlockPos pos = owner.hexwright$grantPos();
            if (pos != null) {
                return pos;
            }
        }
        if (env instanceof CircleCastEnv circle) {
            return circle.circleState().impetusPos;
        }
        return null;
    }

    private static @Nullable Entity grantEntity(CastingEnvironment env) {
        if (env instanceof MediaGrantOwner owner) {
            Entity entity = owner.hexwright$grantEntity();
            if (entity != null) {
                return entity;
            }
        }
        return env.getCastingEntity();
    }


    private static void emitParticles() {
        for (Iterator<WeakReference<Entity>> it = ENTITY_ROSTER.iterator(); it.hasNext(); ) {
            Entity entity = it.next().get();
            if (entity == null || entity.isRemoved() || remaining(entity) <= 0) {
                it.remove();
                continue;
            }
            if (entity.level() instanceof ServerLevel level) {
                AABB box = entity.getBoundingBox();
                spray(level, box.getCenter(), Math.max(0.4, box.getXsize()), box.getYsize() * 0.5);
            }
        }

        if (server != null) {
            for (Map.Entry<GlobalPos, Long> entry : BLOCK_GRANTS.entrySet()) {
                if (entry.getValue() <= 0) {
                    continue;
                }
                ServerLevel level = server.getLevel(entry.getKey().dimension());
                if (level == null || !level.isLoaded(entry.getKey().pos())) {
                    continue;
                }
                spray(level, Vec3.atCenterOf(entry.getKey().pos()), 0.8, 0.4);
            }
        }
        BLOCK_GRANTS.values().removeIf(held -> held <= 0);
    }

    private static void spray(ServerLevel level, Vec3 centre, double spread, double height) {
        level.sendParticles(new ConjureParticleOptions(GRANT_COLOR),
            centre.x, centre.y, centre.z, PARTICLE_MOTES,
            spread * 0.5, height + 0.2, spread * 0.5, 0.0);
    }

    public static long dust(double amount) {
        return (long) (amount * MediaConstants.DUST_UNIT);
    }
}
