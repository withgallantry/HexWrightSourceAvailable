package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.hexweave.WeaveTrigger.Kind;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class WovenOrbTriggers {

    public static final ResourceLocation KEY_C2S = Hexwright.id("woven_orb_key");
    private static final int MAX_KEYS_HELD = 16;

    static final int INPUT_DELAY = 5;
    static final int SENSOR_DELAY = 10;
    static final int MAX_ARRIVALS_PER_TICK = 8;
    static final double PROJECTILE_REACH = 5.0;
    static final double PROJECTILE_MISS = 1.5;
    static final double TARGETED_REACH = 24.0;
    static final double GAZE_REACH = 32.0;
    static final int GAZE_TICKS = 20;
    static final int PARRY_TICKS = 8;
    static final float FALL_DISTANCE = 4.0f;

    private static final Map<UUID, Holder> HOLDERS = new HashMap<>();

    public record Fired(Kind kind, String key, InteractionHand hand, WovenOrbCasting.Result result) {
    }

    private static final class Holder {
        final Set<String> keysHeld = new HashSet<>();
        final Hand[] hands = {new Hand(), new Hand()};
    }

    private static final class Hand {
        final Map<String, Long> ready = new HashMap<>();
        final Map<String, Watch> watches = new HashMap<>();
        long parryUntil = Long.MIN_VALUE;
        int gazeAt = -1;
        int gazeTicks;
        boolean falling;

        void forgetSenses() {
            watches.clear();
            gazeAt = -1;
            falling = false;
        }
    }

    private static final class Watch {
        Set<Integer> seen = Set.of();
        final Map<Integer, Long> waiting = new HashMap<>();
    }

    private WovenOrbTriggers() {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(KEY_C2S, (server, player, handler, buf, responseSender) -> {
            String key = buf.readUtf(WeaveTrigger.MAX_KEY_LENGTH);
            boolean down = buf.readBoolean();
            boolean using = buf.readBoolean();
            server.execute(() -> {
                HexwrightDebug.log(HexwrightDebug.WEAVE, "[weave] {}: key {} {} (use button {})",
                    player.getScoreboardName(), key, down ? "down" : "up", using ? "held" : "up");
                onKey(player, key, down, using);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tick(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
    }

    static void forget(ServerPlayer player) {
        HOLDERS.remove(player.getUUID());
    }

    private static Holder holder(ServerPlayer player) {
        return HOLDERS.computeIfAbsent(player.getUUID(), id -> new Holder());
    }

    private static boolean sensor(Kind kind) {
        return switch (kind) {
            case ON_USE, SNEAK_USE, KEY, KEY_USE -> false;
            default -> true;
        };
    }

    private static @Nullable Fired fire(ServerPlayer player, InteractionHand hand, ItemStack orb, Kind kind,
                                        String key, @Nullable Iota seed) {
        Hand state = holder(player).hands[hand.ordinal()];
        long now = player.level().getGameTime();
        String wait = kind.id() + " " + key;
        if (now < state.ready.getOrDefault(wait, Long.MIN_VALUE)) {
            return null;
        }
        state.ready.put(wait, now + (sensor(kind) ? SENSOR_DELAY : INPUT_DELAY));
        return new Fired(kind, key, hand, WovenOrbCasting.activate(player, hand, orb, kind, key, seed));
    }

    private static Fired fireFor(ServerPlayer player, InteractionHand hand, ItemStack orb, Kind kind, int reach,
                                 Entity cause) {
        WovenOrbCasting.Result result = kind.ranged()
            ? WovenOrbCasting.activate(player, hand, orb, kind, reach, new EntityIota(cause))
            : WovenOrbCasting.activate(player, hand, orb, kind, "", new EntityIota(cause));
        return new Fired(kind, "", hand, result);
    }

    private static boolean has(List<WeaveTrigger> triggers, Kind kind) {
        return triggers.stream().anyMatch(trigger -> trigger.kind() == kind);
    }

    private static boolean has(List<WeaveTrigger> triggers, Kind kind, String key) {
        return triggers.stream().anyMatch(trigger -> trigger.kind() == kind && trigger.key().equals(key));
    }

    private static boolean able(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator();
    }


    static List<Fired> onUse(ServerPlayer player, InteractionHand hand, ItemStack orb) {
        List<WeaveTrigger> triggers = WovenOrbItem.listensFor(orb);
        Holder holder = holder(player);
        List<Fired> fired = new ArrayList<>();
        if (has(triggers, Kind.PARRY)) {
            holder.hands[hand.ordinal()].parryUntil = player.level().getGameTime() + PARRY_TICKS;
        }
        boolean chorded = false;
        for (String key : holder.keysHeld) {
            if (has(triggers, Kind.KEY_USE, key)) {
                chorded = true;
                add(fired, fire(player, hand, orb, Kind.KEY_USE, key, new EntityIota(player)));
            }
        }
        if (chorded) {
            return fired;
        }
        Kind kind = player.isShiftKeyDown() && has(triggers, Kind.SNEAK_USE) ? Kind.SNEAK_USE
            : has(triggers, Kind.ON_USE) ? Kind.ON_USE : null;
        if (kind != null) {
            add(fired, fire(player, hand, orb, kind, "", new EntityIota(player)));
        }
        return fired;
    }

    static List<Fired> onKey(ServerPlayer player, String key, boolean down, boolean using) {
        Holder holder = holder(player);
        List<Fired> fired = new ArrayList<>();
        if (!down) {
            holder.keysHeld.remove(key);
            return fired;
        }
        if (holder.keysHeld.size() < MAX_KEYS_HELD) {
            holder.keysHeld.add(key);
        }
        if (able(player)) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack orb = player.getItemInHand(hand);
                List<WeaveTrigger> triggers = WovenOrbItem.listensFor(orb);
                Kind kind = using && has(triggers, Kind.KEY_USE, key) ? Kind.KEY_USE
                    : has(triggers, Kind.KEY, key) ? Kind.KEY : null;
                if (kind != null) {
                    add(fired, fire(player, hand, orb, kind, key, new EntityIota(player)));
                }
            }
        }
        return fired;
    }

    public static List<Fired> onStruck(ServerPlayer player, DamageSource source) {
        Holder holder = HOLDERS.get(player.getUUID());
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        List<Fired> fired = new ArrayList<>();
        if (holder == null || attacker == null || !able(player)) {
            return fired;
        }
        long now = player.level().getGameTime();
        for (InteractionHand hand : InteractionHand.values()) {
            Hand state = holder.hands[hand.ordinal()];
            if (now <= state.parryUntil) {
                state.parryUntil = Long.MIN_VALUE;
                ItemStack orb = player.getItemInHand(hand);
                if (has(WovenOrbItem.listensFor(orb), Kind.PARRY)) {
                    add(fired, fire(player, hand, orb, Kind.PARRY, "", new EntityIota(attacker)));
                }
            }
        }
        return fired;
    }


    static List<Fired> tick(ServerPlayer player) {
        Holder holder = HOLDERS.get(player.getUUID());
        List<Fired> fired = new ArrayList<>();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack orb = player.getItemInHand(hand);
            List<WeaveTrigger> triggers = able(player) ? WovenOrbItem.listensFor(orb) : List.of();
            Set<Kind> kinds = EnumSet.noneOf(Kind.class);
            triggers.forEach(trigger -> kinds.add(trigger.kind()));
            if (kinds.stream().noneMatch(WovenOrbTriggers::sensor)) {
                if (holder != null) {
                    holder.hands[hand.ordinal()].forgetSenses();
                }
                continue;
            }
            sense(player, hand, orb, triggers, kinds, holder(player).hands[hand.ordinal()], fired);
        }
        return fired;
    }

    private static void sense(ServerPlayer player, InteractionHand hand, ItemStack orb, List<WeaveTrigger> triggers,
                              Set<Kind> kinds, Hand state, List<Fired> fired) {
        ServerLevel level = player.serverLevel();
        AABB body = player.getBoundingBox();
        long now = level.getGameTime();
        Set<String> watched = new HashSet<>();

        Set<Integer> reaches = new TreeSet<>();
        triggers.stream().filter(trigger -> trigger.kind() == Kind.PROXIMITY).forEach(trigger -> reaches.add(trigger.reach()));
        for (int reach : reaches) {
            String sensor = "proximity " + reach;
            watched.add(sensor);
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, body.inflate(reach),
                entity -> entity != player && entity.isAlive() && !entity.isSpectator());
            for (LivingEntity arrived : arrivals(state, sensor, near, player, now)) {
                fired.add(fireFor(player, hand, orb, Kind.PROXIMITY, reach, arrived));
            }
        }

        if (kinds.contains(Kind.PROJECTILE)) {
            watched.add("projectile");
            List<Projectile> incoming = level.getEntitiesOfClass(Projectile.class, body.inflate(PROJECTILE_REACH),
                projectile -> projectile.getOwner() != player && headingAt(projectile, player, 20));
            for (Projectile shot : arrivals(state, "projectile", incoming, player, now)) {
                fired.add(fireFor(player, hand, orb, Kind.PROJECTILE, 0, shot));
            }
        }

        if (kinds.contains(Kind.TARGETED)) {
            watched.add("targeted");
            List<Mob> hunters = level.getEntitiesOfClass(Mob.class, body.inflate(TARGETED_REACH),
                mob -> mob.isAlive() && mob.getTarget() == player);
            for (Mob hunter : arrivals(state, "targeted", hunters, player, now)) {
                fired.add(fireFor(player, hand, orb, Kind.TARGETED, 0, hunter));
            }
        }
        state.watches.keySet().retainAll(watched);

        Entity seen = kinds.contains(Kind.GAZE) ? lookedAt(player) : null;
        if (seen == null || seen.getId() != state.gazeAt) {
            state.gazeAt = seen == null ? -1 : seen.getId();
            state.gazeTicks = 0;
        } else if (++state.gazeTicks == GAZE_TICKS) {
            add(fired, fire(player, hand, orb, Kind.GAZE, "", new EntityIota(seen)));
        }

        boolean airborne = kinds.contains(Kind.FALLING) && !player.onGround() && !player.isInWater()
            && !player.isFallFlying() && !player.getAbilities().flying;
        if (!airborne) {
            state.falling = false;
        } else if (!state.falling && player.fallDistance >= FALL_DISTANCE) {
            state.falling = true;
            add(fired, fire(player, hand, orb, Kind.FALLING, "", new EntityIota(player)));
        }
    }

    private static <T extends Entity> List<T> arrivals(Hand state, String sensor, List<T> now, ServerPlayer player,
                                                       long tick) {
        Watch watch = state.watches.computeIfAbsent(sensor, name -> new Watch());
        watch.waiting.values().removeIf(until -> until <= tick);
        List<T> fresh = now.stream().filter(entity -> !watch.seen.contains(entity.getId()))
            .sorted(Comparator.comparingDouble(player::distanceToSqr)).toList();
        List<T> firing = new ArrayList<>();
        Set<Integer> deferred = new HashSet<>();
        for (T entity : fresh) {
            if (watch.waiting.containsKey(entity.getId())) {
                continue;
            }
            if (firing.size() < MAX_ARRIVALS_PER_TICK) {
                firing.add(entity);
                watch.waiting.put(entity.getId(), tick + SENSOR_DELAY);
            } else {
                deferred.add(entity.getId());
            }
        }
        Set<Integer> seen = new HashSet<>(ids(now));
        seen.removeAll(deferred);
        watch.seen = seen;
        return firing;
    }

    private static Set<Integer> ids(List<? extends Entity> entities) {
        if (entities.isEmpty()) {
            return Set.of();
        }
        Set<Integer> ids = new HashSet<>();
        entities.forEach(entity -> ids.add(entity.getId()));
        return ids;
    }

    public static List<Fired> beforeMove(Projectile projectile) {
        return projectile.level() instanceof ServerLevel level ? beforeMove(projectile, level.players()) : List.of();
    }

    static List<Fired> beforeMove(Projectile projectile, List<ServerPlayer> players) {
        List<Fired> fired = new ArrayList<>();
        List<ServerPlayer> struck = players.stream()
            .filter(player -> able(player) && player != projectile.getOwner() && headingAt(projectile, player, 1))
            .toList();
        long now = projectile.level().getGameTime();
        int id = projectile.getId();
        for (ServerPlayer player : struck) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack orb = player.getItemInHand(hand);
                if (projectile.isRemoved() || !has(WovenOrbItem.listensFor(orb), Kind.PROJECTILE)
                    || !headingAt(projectile, player, 1)) {
                    continue;
                }
                Watch watch = holder(player).hands[hand.ordinal()].watches
                    .computeIfAbsent("projectile", name -> new Watch());
                watch.waiting.values().removeIf(until -> until <= now);
                if (watch.seen.contains(id) || watch.waiting.containsKey(id) || !bringWithinReach(projectile, player)) {
                    continue;
                }
                watch.waiting.put(id, now + SENSOR_DELAY);
                HexwrightDebug.log(HexwrightDebug.WEAVE, "[weave] {}: caught {} moving {} blocks a tick",
                    player.getScoreboardName(), projectile, projectile.getDeltaMovement().length());
                fired.add(fireFor(player, hand, orb, Kind.PROJECTILE, 0, projectile));
            }
        }
        return fired;
    }

    private static boolean bringWithinReach(Projectile projectile, ServerPlayer player) {
        Vec3 from = projectile.position();
        Vec3 toCentre = player.getBoundingBox().getCenter().subtract(from);
        if (toCentre.lengthSqr() <= PROJECTILE_REACH * PROJECTILE_REACH) {
            return true;
        }
        Vec3 heading = projectile.getDeltaMovement().normalize();
        double along = toCentre.dot(heading);
        double offPath = toCentre.lengthSqr() - along * along;
        Vec3 to = from.add(heading.scale(along - Math.sqrt(PROJECTILE_REACH * PROJECTILE_REACH - offPath)));

        Level level = projectile.level();
        if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile))
            .getType() != HitResult.Type.MISS) {
            return false;
        }
        Entity owner = projectile.getOwner();
        AABB path = projectile.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0);
        if (ProjectileUtil.getEntityHitResult(level, projectile, from, to, path, entity -> entity != player
            && entity != owner && !entity.isSpectator() && entity.isPickable()) != null) {
            return false;
        }
        projectile.setPos(to);
        return true;
    }

    private static boolean headingAt(Projectile projectile, ServerPlayer player, int ticks) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speed = velocity.lengthSqr();
        if (speed < 0.01) {
            return false;
        }
        Vec3 centre = player.getBoundingBox().getCenter();
        double nearestAt = centre.subtract(projectile.position()).dot(velocity) / speed;
        if (nearestAt < 0) {
            return false;
        }
        return projectile.position().add(velocity.scale(Math.min(nearestAt, ticks))).distanceToSqr(centre)
            <= PROJECTILE_MISS * PROJECTILE_MISS;
    }

    private static @Nullable Entity lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = player.pick(GAZE_REACH, 1.0f, false).getLocation();
        AABB search = player.getBoundingBox().expandTowards(look.scale(GAZE_REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, search,
            entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity.isPickable());
        return hit == null ? null : hit.getEntity();
    }

    private static void add(List<Fired> fired, @Nullable Fired one) {
        if (one != null) {
            fired.add(one);
        }
    }
}
