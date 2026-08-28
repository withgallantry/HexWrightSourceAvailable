package com.bluup.hexwright.server.portal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortalManager extends SavedData {

    private static final String STORAGE_ID = "hexwright_portals";

    public static final int MAX_PAIRS_PER_CASTER = 3;

    private static final double DISMISS_PLANE_TOLERANCE = 1.0;

    private static final int CROSSING_COOLDOWN_TICKS = 3;

    private static final double MAX_CROSSING_DEPTH = 2.5;

    private static final double MAX_CROSSING_STEP = 4.0;

    private static final double CROSSING_MARGIN = 0.3;

    private static final float PORTAL_OPEN_PITCH = 1.0f;

    private static final float PORTAL_CLOSE_PITCH = 0.7f;

    private static final float PORTAL_PITCH_VARIANCE = 0.15f;

    private static final double GATHER_MARGIN = MAX_CROSSING_STEP;

    private static final double REFUSED_STANDOFF = 0.5;

    private static final int LANDING_FREEZE_TICKS = 20;

    private record LandingPin(Vec3 pos, int ticksLeft) {
    }

    @FunctionalInterface
    public interface CrossingGuard {
        boolean mayCross(PortalPair pair, int side, Entity entity);
    }

    private enum CrossResult {
        NONE,
        CROSSED,
        REFUSED
    }

    private static CrossingGuard crossingGuard = (pair, side, entity) -> true;

    public static void setCrossingGuard(CrossingGuard guard) {
        crossingGuard = guard;
    }

    private final List<PortalPair> pairs = new ArrayList<>();

    private final Map<Integer, Integer> crossingCooldowns = new HashMap<>();

    private final Map<Integer, Vec3> lastCenters = new HashMap<>();

    private final Map<UUID, LandingPin> landingFreeze = new HashMap<>();

    public void beginLandingFreeze(UUID uuid, Vec3 pos) {
        landingFreeze.put(uuid, new LandingPin(pos, LANDING_FREEZE_TICKS));
    }

    public static PortalManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PortalManager::load, PortalManager::new, STORAGE_ID);
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(level -> get(level).tick(level));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            get(handler.player.serverLevel()).syncAllTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
            get(destination).syncAllTo(player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            PortalInteraction.forget(handler.player));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright_portals")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear").executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    PortalManager manager = get(level);
                    int removed = manager.pairs.size();
                    for (PortalPair pair : List.copyOf(manager.pairs)) {
                        manager.removePair(level, pair.id());
                    }
                    context.getSource().sendSuccess(
                        () -> Component.literal("Removed " + removed + " portal pair(s)"), true);
                    return Command.SINGLE_SUCCESS;
                }))));
    }


    public List<PortalPair> pairs() {
        return pairs;
    }

    public @Nullable PortalPair byId(UUID id) {
        for (PortalPair pair : pairs) {
            if (pair.id().equals(id)) {
                return pair;
            }
        }
        return null;
    }

    public @Nullable PortalPair findCoincident(PortalWindow window) {
        for (PortalPair pair : pairs) {
            if (pair.isSessionBound()) {
                continue;
            }
            if (pair.window(0).roughlyMatches(window) || pair.window(1).roughlyMatches(window)) {
                return pair;
            }
        }
        return null;
    }

    public @Nullable PortalPair findContaining(Vec3 point, ResourceKey<Level> dimension) {
        PortalPair best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PortalPair pair : pairs) {
            if (pair.isSessionBound()) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                if (!pair.sideIn(side, dimension)) {
                    continue;
                }
                PortalWindow window = pair.window(side);
                if (Math.abs(window.signedDistance(point)) > DISMISS_PLANE_TOLERANCE
                    || !window.containsProjected(point, 0.0)) {
                    continue;
                }
                double distance = window.distanceSqToRect(point);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pair;
                }
            }
        }
        return best;
    }


    public void addPair(ServerLevel level, PortalPair pair) {
        List<PortalPair> owned = new ArrayList<>();
        if (!pair.isSessionBound()) {
            for (PortalPair existing : pairs) {
                if (!existing.isSessionBound() && existing.caster().equals(pair.caster())) {
                    owned.add(existing);
                }
            }
        }
        while (owned.size() >= MAX_PAIRS_PER_CASTER) {
            PortalPair oldest = owned.get(0);
            for (PortalPair candidate : owned) {
                if (candidate.createdGameTime() < oldest.createdGameTime()) {
                    oldest = candidate;
                }
            }
            owned.remove(oldest);
            removePair(level, oldest.id());
        }

        pairs.add(pair);
        setDirty();
        HexwrightNetworking.broadcastPortalAdd(level, pair);
        SoundEvent openSound = pair.isSessionBound()
            ? HexwrightSoundEvents.portalOpenClose()
            : HexwrightSoundEvents.janusOpen();
        playPortalSound(level, pair, openSound, PORTAL_OPEN_PITCH);
    }

    public void removePair(ServerLevel level, UUID id) {
        PortalPair pair = byId(id);
        if (pair == null) {
            return;
        }
        pairs.remove(pair);
        setDirty();
        PortalInteraction.forgetPair(id);
        HexwrightNetworking.broadcastPortalRemove(level, id);
        playPortalSound(level, pair, HexwrightSoundEvents.portalOpenClose(), PORTAL_CLOSE_PITCH);
    }

    private static void playPortalSound(ServerLevel level, PortalPair pair, SoundEvent sound, float basePitch) {
        for (int side = 0; side < 2; side++) {
            if (!pair.sideIn(side, level.dimension())) {
                continue;
            }
            Vec3 pos = pair.window(side).center();
            float pitch = basePitch + (level.random.nextFloat() - 0.5f) * PORTAL_PITCH_VARIANCE;
            level.playSound(null, pos.x, pos.y, pos.z,
                sound, SoundSource.BLOCKS, 1.0f, pitch);
        }
    }

    public void syncAllTo(ServerPlayer player) {
        HexwrightNetworking.sendPortalSync(player, pairs);
    }


    private void tick(ServerLevel level) {
        if (!landingFreeze.isEmpty()) {
            var iterator = landingFreeze.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Entity entity = level.getEntity(entry.getKey());
                LandingPin pin = entry.getValue();
                if (entity == null || entity.isRemoved()) {
                    iterator.remove();
                    continue;
                }
                entity.setPos(pin.pos().x, pin.pos().y, pin.pos().z);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.fallDistance = 0.0f;
                if (pin.ticksLeft() <= 1) {
                    iterator.remove();
                } else {
                    entry.setValue(new LandingPin(pin.pos(), pin.ticksLeft() - 1));
                }
            }
        }

        long gameTime = level.getGameTime();
        for (PortalPair pair : List.copyOf(pairs)) {
            if (pair.isExpired(gameTime)) {
                removePair(level, pair.id());
            }
        }

        if (pairs.isEmpty()) {
            crossingCooldowns.clear();
            lastCenters.clear();
            return;
        }
        crossingCooldowns.replaceAll((id, ticks) -> ticks - 1);
        crossingCooldowns.values().removeIf(ticks -> ticks <= 0);

        Map<Integer, Entity> candidates = new HashMap<>();
        for (PortalPair pair : pairs) {
            for (int side = 0; side < 2; side++) {
                if (!pair.sideIn(side, level.dimension())) {
                    continue;
                }
                AABB tracked = pair.window(side).bounds(GATHER_MARGIN).inflate(GATHER_MARGIN);
                for (Entity entity : level.getEntities((Entity) null, tracked,
                    e -> foldsThrough(e) && !e.isPassenger() && !e.isRemoved())) {
                    candidates.put(entity.getId(), entity);
                }
            }
        }

        for (Entity entity : candidates.values()) {
            boolean held = holdOut(level, entity, gameTime);
            Vec3 now = center(entity);
            Vec3 prev = held ? now : lastCenters.get(entity.getId());
            if (prev != null && prev.distanceToSqr(now) > MAX_CROSSING_STEP * MAX_CROSSING_STEP) {
                prev = null;
            }
            if (prev != null && !crossingCooldowns.containsKey(entity.getId())) {
                for (PortalPair pair : pairs) {
                    if (!pair.isOpen(gameTime)) {
                        continue;
                    }
                    CrossResult result = CrossResult.NONE;
                    for (int side = 0; side < 2 && result == CrossResult.NONE; side++) {
                        if (!pair.sideIn(side, level.dimension())) {
                            continue;
                        }
                        result = tryCross(pair, side, entity, prev, now);
                    }
                    if (result != CrossResult.NONE) {
                        if (result == CrossResult.CROSSED) {
                            crossingCooldowns.put(entity.getId(), CROSSING_COOLDOWN_TICKS);
                        }
                        now = center(entity);
                        break;
                    }
                }
            }
            lastCenters.put(entity.getId(), now);
        }

        lastCenters.keySet().retainAll(candidates.keySet());
    }

    private static Vec3 center(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    public static boolean foldsThrough(Entity entity) {
        return entity instanceof LivingEntity
            || entity instanceof ItemEntity
            || entity instanceof Projectile
            || entity instanceof ExperienceOrb;
    }

    private CrossResult tryCross(PortalPair pair, int side, Entity entity, Vec3 prev, Vec3 now) {
        PortalWindow window = pair.window(side);
        double sNow = window.signedDistance(now);
        double sPrev = window.signedDistance(prev);
        double depthAllowance = Math.max(MAX_CROSSING_DEPTH, prev.distanceTo(now));
        if ((sPrev > 0.0) == (sNow > 0.0) || Math.abs(sPrev) > depthAllowance) {
            return CrossResult.NONE;
        }
        double t = sPrev / (sPrev - sNow);
        Vec3 pierce = prev.add(now.subtract(prev).scale(t));
        if (!window.containsProjected(pierce, CROSSING_MARGIN)) {
            return CrossResult.NONE;
        }

        if (!crossingGuard.mayCross(pair, side, entity)) {
            rebound(window, entity, sPrev);
            return CrossResult.REFUSED;
        }

        applyTeleport(pair, side, entity, sPrev, false);
        return CrossResult.CROSSED;
    }

    private boolean holdOut(ServerLevel level, Entity entity, long gameTime) {
        boolean moved = false;
        for (PortalPair pair : pairs) {
            if (!pair.isOpen(gameTime)) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                if (!pair.sideIn(side, level.dimension())) {
                    continue;
                }
                PortalWindow window = pair.window(side);
                Vec3 center = center(entity);
                double signed = window.signedDistance(center);
                if (Math.abs(signed) >= REFUSED_STANDOFF
                    || !window.containsProjected(center, CROSSING_MARGIN)) {
                    continue;
                }
                if (crossingGuard.mayCross(pair, side, entity)) {
                    continue;
                }
                rebound(window, entity, signed);
                moved = true;
            }
        }
        return moved;
    }

    private static void rebound(PortalWindow window, Entity entity, double entrySign) {
        Vec3 outward = window.normal().scale(entrySign >= 0.0 ? 1.0 : -1.0);
        double depth = window.signedDistance(center(entity)) * (entrySign >= 0.0 ? 1.0 : -1.0);
        Vec3 newPos = depth >= REFUSED_STANDOFF
            ? entity.position()
            : entity.position().add(outward.scale(REFUSED_STANDOFF - depth));

        Vec3 velocity = entity.getDeltaMovement();
        double inward = velocity.dot(outward);
        Vec3 newVel = inward < 0.0 ? velocity.subtract(outward.scale(inward)) : velocity;

        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(newPos.x, newPos.y, newPos.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(newVel);
            player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), newVel));
        } else {
            entity.teleportTo(newPos.x, newPos.y, newPos.z);
            entity.setDeltaMovement(newVel);
            entity.hasImpulse = true;
        }
    }

    private void applyTeleport(PortalPair pair, int side, Entity entity, double entrySign, boolean predicted) {
        PortalTransform transform = pair.transformFrom(side);
        PortalWindow source = pair.window(side);
        PortalWindow destination = pair.window(1 - side);
        Vec3 newPos = exitPosition(transform, source, destination, entity.position(), entrySign);
        Vec3 newVel = transform.applyDirection(entity.getDeltaMovement());
        float newYaw = Mth.wrapDegrees(entity.getYRot() + transform.yawDeltaDegrees());

        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ServerLevel destLevel = level;
        var destDim = pair.dimensionOr(1 - side, level.dimension());
        if (!destDim.equals(level.dimension())) {
            destLevel = level.getServer().getLevel(destDim);
            if (destLevel == null) {
                return;
            }
        }

        if (!(Double.isFinite(newPos.x) && Double.isFinite(newPos.y) && Double.isFinite(newPos.z))
            || !(newPos.y >= destLevel.getMinBuildHeight() && newPos.y <= destLevel.getMaxBuildHeight())) {
            return;
        }
        destLevel.getChunkAt(BlockPos.containing(newPos));

        if (destLevel != level) {
            applyCrossDimensionalTeleport(destLevel, entity, newPos, newVel, newYaw, transform.yawDeltaDegrees());
            return;
        }

        if (entity instanceof ServerPlayer player) {
            if (predicted) {
                player.moveTo(newPos.x, newPos.y, newPos.z, newYaw, player.getXRot());
                player.setDeltaMovement(newVel);
                player.connection.teleport(newPos.x, newPos.y, newPos.z,
                    player.getYRot(), player.getXRot(), RelativeMovement.ALL);
            } else {
                player.connection.teleport(newPos.x, newPos.y, newPos.z, newYaw, player.getXRot());
                player.setDeltaMovement(newVel);
                player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), newVel));
            }
        } else {
            float headDelta = transform.yawDeltaDegrees();
            entity.teleportTo(newPos.x, newPos.y, newPos.z);
            entity.setYRot(newYaw);
            if (entity instanceof LivingEntity living) {
                living.yBodyRot = Mth.wrapDegrees(living.yBodyRot + headDelta);
                living.yHeadRot = Mth.wrapDegrees(living.yHeadRot + headDelta);
                living.yBodyRotO = living.yBodyRot;
                living.yHeadRotO = living.yHeadRot;
            }
            entity.setDeltaMovement(newVel);
            entity.hasImpulse = true;
        }
    }

    private static void applyCrossDimensionalTeleport(ServerLevel destLevel, Entity entity,
                                                      Vec3 newPos, Vec3 newVel, float newYaw, float headDelta) {
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(destLevel, newPos.x, newPos.y, newPos.z, newYaw, player.getXRot());
            player.setDeltaMovement(newVel);
            player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), newVel));
            get(destLevel).beginLandingFreeze(player.getUUID(), newPos);
            return;
        }
        UUID uuid = entity.getUUID();
        if (!entity.teleportTo(destLevel, newPos.x, newPos.y, newPos.z,
            Set.of(), newYaw, entity.getXRot())) {
            return;
        }
        Entity moved = destLevel.getEntity(uuid);
        if (moved != null) {
            moved.setYRot(newYaw);
            if (moved instanceof LivingEntity living) {
                living.yBodyRot = newYaw;
                living.yHeadRot = newYaw;
                living.yBodyRotO = living.yBodyRot;
                living.yHeadRotO = living.yHeadRot;
            }
            moved.setDeltaMovement(newVel);
            moved.hasImpulse = true;
        }
    }

    public static Vec3 exitPosition(PortalTransform transform, PortalWindow source, PortalWindow destination,
                                    Vec3 pos, double entrySign) {
        Vec3 onPlane = pos.subtract(source.normal().scale(source.signedDistance(pos)));
        double clearance = (entrySign >= 0.0 ? 1.0 : -1.0) * PortalPair.EXIT_CLEARANCE;
        return transform.apply(onPlane).add(destination.normal().scale(clearance));
    }


    private static final double CLIENT_CROSSING_MARGIN = 0.75;

    public static void clientCross(ServerPlayer player, UUID pairId, int side) {
        if ((side != 0 && side != 1) || player.isSpectator() || player.isPassenger()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PortalManager manager = get(level);
        PortalPair pair = manager.byId(pairId);
        if (pair == null || !pair.isOpen(level.getGameTime())) {
            return;
        }
        if (pair.isCrossDimensional() || !pair.sideIn(side, level.dimension())) {
            return;
        }
        if (manager.crossingCooldowns.containsKey(player.getId())) {
            return;
        }
        PortalWindow window = pair.window(side);
        Vec3 c = center(player);
        double signedDist = window.signedDistance(c);
        if (Math.abs(signedDist) > MAX_CROSSING_DEPTH || !window.containsProjected(c, CLIENT_CROSSING_MARGIN)) {
            return;
        }
        if (!crossingGuard.mayCross(pair, side, player)) {
            rebound(window, player, signedDist);
            return;
        }
        manager.applyTeleport(pair, side, player, signedDist, true);
        manager.crossingCooldowns.put(player.getId(), CROSSING_COOLDOWN_TICKS);
        manager.lastCenters.put(player.getId(), center(player));
    }


    private PortalManager() {
    }

    private static PortalManager load(CompoundTag tag) {
        PortalManager manager = new PortalManager();
        ListTag entries = tag.getList("Pairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            try {
                PortalPair pair = PortalPair.load(entries.getCompound(i));
                if (pair.isValid()) {
                    manager.pairs.add(pair);
                } else {
                    Hexwright.LOGGER.warn("Dropping degenerate portal pair {} from save", pair.id());
                }
            } catch (RuntimeException e) {
                Hexwright.LOGGER.warn("Dropping malformed portal pair from save", e);
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (PortalPair pair : pairs) {
            if (pair.isSessionBound()) {
                continue;
            }
            entries.add(pair.save());
        }
        tag.put("Pairs", entries);
        return tag;
    }
}
